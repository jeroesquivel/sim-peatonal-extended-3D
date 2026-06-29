package ar.edu.itba.simped.core;


//Location of targets implemented in geometry
public class TargetLocation {
    //block_name, figure_type, radius, x1, y1, z1, x2, y2, z2
    private double r, x1, x2, y1, y2;
    private TargetLocationGeometryType LocationType;

    public TargetLocation(TargetLocationGeometryType LocationType, double r, double x1, double y1, double x2, double y2 ) {
        this.LocationType = LocationType;
        this.r = r;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
    }

    /**
     * Return distance to the target location from the given coordinates.
     * @param Ax X-coordinate of agent
     * @param Ay Y-coordinate of agent
     *
     * @return Double, The distance between the given coordinates and the Target location
     * */
    public double getDistanceToTarget(double Ax, double Ay){
        double distance = 0;
        switch (this.LocationType){
            case POINT:
                distance = Math.sqrt(Math.pow((Ax - this.x1),2) + Math.pow((Ay - this.y1),2));
                break;

            case CIRCLE:
                distance = Math.sqrt(Math.pow((Ax - this.x1),2) + Math.pow((Ay - this.y1),2)) - this.r;
                break;

            case LINE:
                double abX = x1 - x2, abY = y1 - y2;
                double apX = x1 - Ax, apY = y1 - Ay;

                double cross = apX * abY - apY * abX;   // |AP × AB|
                double abLen = Math.sqrt(abX * abX + abY * abY);

                if (abLen == 0) throw new IllegalArgumentException("A and B must be distinct points");

                distance = Math.abs(cross) / abLen;
                break;

        }
        return distance;
    }

}
