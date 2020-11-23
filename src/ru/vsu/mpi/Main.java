package ru.vsu.mpi;

import mpi.MPI;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

/**
 * Текучев Олег Алексеевич
 * #6
 * Вычислить значения суммы ряда в n точках заданного интервала [A, B] с точностью epsilon
 *
 *       1             1         1 * 3         1 * 3 * 5
 * ------------- = 1 + - * x^2 + ----- * x^4 + --------- * x^6 + ...
 * sqrt(1 - x^2)       2         2 * 4         2 * 4 * 6
 *
 * -1 < x < 1
 *
 * Запуск: mpjrun.sh -np <num of processes> ru.vsu.mpi.Main <start interval> <end interval> <number of points> <epsilon>
 */
public class Main {

    public static final int MASTER_RANK = 0;
    public static final String INTERVAL_REGEXP = "^(-?\\d+\\.\\d+)[,\\s*](-?\\d+\\.\\d+)$";
    public static final String DOUBLE_NUMBER_REGEXP = "^-?0(\\.\\d+)?$";
    public static final String INTEGER_NUMBER_REGEXP = "^\\d+$";
    public static final String EPS_REGEXP = "^0\\.\\d+$";


    public static void main(String[] args) {
        try {
            MPI.Init(args);
            int rank = MPI.COMM_WORLD.Rank();
            int processCount = MPI.COMM_WORLD.Size();
            if (rank == MASTER_RANK) {
                masterProcess(args, processCount);
            } else {
                slaveProcess(rank, processCount);
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            MPI.COMM_WORLD.Bcast(new boolean[]{true}, 0, 1, MPI.BOOLEAN, MASTER_RANK);
        } finally {
            MPI.Finalize();
        }
    }

    private static void masterProcess(String[] args, int processCount) {
        SeriesArguments seriesArguments = readSeriesArgumentsFromCla(args);
        MPI.COMM_WORLD.Bcast(new boolean[]{false}, 0, 1, MPI.BOOLEAN, MASTER_RANK);
        double eps = seriesArguments.getEps();
        MPI.COMM_WORLD.Bcast(new double[]{eps}, 0, 1, MPI.DOUBLE, MASTER_RANK);
        double[] seriesArgs = getDoubleRange(seriesArguments.getStart(), seriesArguments.getEnd(), seriesArguments.getNumber());
        System.out.println("Generated sequence: " + Arrays.toString(seriesArgs));
        int[] sendCount = createSendCount(seriesArgs.length, processCount);
        MPI.COMM_WORLD.Bcast(sendCount, 0, sendCount.length, MPI.INT, MASTER_RANK);
        double[] recvBuff = new double[sendCount[MASTER_RANK]];
        int[] displ = createDisplFromSendCount(sendCount);
        MPI.COMM_WORLD.Scatterv(seriesArgs, 0, sendCount, displ, MPI.DOUBLE, recvBuff, 0, sendCount[MASTER_RANK], MPI.DOUBLE, MASTER_RANK);
        double[] seriesRes = executeSeries(recvBuff, eps);
        double[] seriesResAll = new double[seriesArgs.length];
        MPI.COMM_WORLD.Gatherv(seriesRes, 0, seriesRes.length, MPI.DOUBLE, seriesResAll, 0, sendCount, displ, MPI.DOUBLE, MASTER_RANK);
        System.out.println("Result: " + Arrays.toString(seriesResAll));
    }

    private static void slaveProcess(int rank, int processCount) {
        boolean[] terminateBuff = new boolean[1];
        MPI.COMM_WORLD.Bcast(terminateBuff, 0, terminateBuff.length, MPI.BOOLEAN, MASTER_RANK);
        if (terminateBuff[0]) {
            return;
        }
        double[] epsBuff = new double[1];
        MPI.COMM_WORLD.Bcast(epsBuff, 0, epsBuff.length, MPI.DOUBLE, MASTER_RANK);
        double eps = epsBuff[0];
        int[] numberCounts = new int[processCount];
        MPI.COMM_WORLD.Bcast(numberCounts, 0, numberCounts.length, MPI.INT, MASTER_RANK);
        double[] recvBuff = new double[numberCounts[rank]];
        MPI.COMM_WORLD.Scatterv(null, 0, null, null, MPI.DOUBLE, recvBuff, 0, numberCounts[rank], MPI.DOUBLE, MASTER_RANK);
        double[] seriesRes = executeSeries(recvBuff, eps);
        MPI.COMM_WORLD.Gatherv(seriesRes, 0, seriesRes.length, MPI.DOUBLE, null, 0, null, null, MPI.DOUBLE, MASTER_RANK);
    }

    private static SeriesArguments readSeriesArgumentsFromCla(String[] args) {
        if (args.length < 7) {
            throw new IllegalArgumentException("Invalid number of arguments");
        }
        if (!args[3].matches(DOUBLE_NUMBER_REGEXP) || !args[4].matches(DOUBLE_NUMBER_REGEXP)) {
            throw new IllegalArgumentException("Invalid interval");
        }
        if (!args[5].matches(INTEGER_NUMBER_REGEXP)) {
            throw new IllegalArgumentException("Invalid count of points");
        }
        if (!args[6].matches(EPS_REGEXP)) {
            throw new IllegalArgumentException("Invalid epsilon");
        }
        double start = Double.parseDouble(args[3]);
        double end = Double.parseDouble(args[4]);
        int n = Integer.parseInt(args[5]);
        double eps = Double.parseDouble(args[6]);
        return new SeriesArguments(start, end, n, eps);
    }

    private static double executeSeries(double value, double eps) {
        int numeratorK = 1;
        int denominatorK = 2;
        double valSqr = Math.pow(value, 2);
        double currStep = numeratorK * valSqr / denominatorK;
        double prevStep;
        double currRes = 1;
        double prevRes;
        do {
            numeratorK += 2;
            denominatorK += 2;
            prevRes = currRes;
            prevStep = currStep;
            currStep = prevStep * (numeratorK * valSqr / denominatorK);
            currRes = prevRes + currStep;
        } while (Math.abs(currRes - prevRes) >= eps);
        return prevRes;
    }

    private static double[] executeSeries(double[] values, double eps) {
        double[] res = new double[values.length];
        for (int i = 0; i < values.length; ++i) {
            res[i] = executeSeries(values[i], eps);
        }
        return res;
    }

    private static int[] createDisplFromSendCount(int[] sendCount) {
        int[] displ = new int[sendCount.length];
        int offset = 0;
        for (int i = 0; i < displ.length; ++i) {
            displ[i] = offset;
            offset += sendCount[i];
        }
        return displ;
    }

    private static double[] getDoubleRange(double start, double end, int n) {
        double step = BigDecimal.valueOf((end - start) / (n - 1))
                .setScale(3, RoundingMode.HALF_EVEN)
                .doubleValue();
        double[] res = new double[n];
        res[0] = start;
        for (int i = 1; i < n; ++i) {
            res[i] = i == n - 1 ? end : res[i - 1] + step;
        }
        return res;
    }

    private static int[] createSendCount(int buffToSendSize, int processCount) {
        int block = buffToSendSize / processCount;
        int mod = buffToSendSize % processCount;
        int[] sendCount = new int[processCount];
        Arrays.fill(sendCount, block);
        for (int i = 0; i < mod; ++i) {
            sendCount[i] += 1;
        }
        return sendCount;
    }
}
