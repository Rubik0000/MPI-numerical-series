package ru.vsu.mpi;

public class SeriesArguments {

    private final double start;
    private final double end;
    private final int number;
    private final double eps;


    public SeriesArguments(double start, double end, int number, double eps) {
        this.start = start;
        this.end = end;
        this.number = number;
        this.eps = eps;
    }


    public double getStart() {
        return start;
    }

    public double getEnd() {
        return end;
    }

    public double getEps() {
        return eps;
    }

    public int getNumber() {
        return number;
    }
}
