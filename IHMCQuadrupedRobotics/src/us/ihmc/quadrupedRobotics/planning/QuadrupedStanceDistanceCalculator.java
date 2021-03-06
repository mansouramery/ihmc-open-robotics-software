package us.ihmc.quadrupedRobotics.planning;

import org.apache.commons.lang3.mutable.MutableDouble;
import us.ihmc.robotics.robotSide.QuadrantDependentList;
import us.ihmc.robotics.robotSide.RobotQuadrant;

import javax.vecmath.Point3d;

public class QuadrupedStanceDistanceCalculator
{
   private QuadrantDependentList<QuadrantDependentList<MutableDouble>> quadrupedDistanceMatrixA;
   private QuadrantDependentList<QuadrantDependentList<MutableDouble>> quadrupedDistanceMatrixB;
   private Point3d solePointA;
   private Point3d solePointB;

   public QuadrupedStanceDistanceCalculator()
   {
      quadrupedDistanceMatrixA = new QuadrantDependentList<>();
      quadrupedDistanceMatrixB = new QuadrantDependentList<>();
      for (RobotQuadrant quadrantA : RobotQuadrant.values())
      {
         quadrupedDistanceMatrixA.set(quadrantA, new QuadrantDependentList<MutableDouble>());
         quadrupedDistanceMatrixB.set(quadrantA, new QuadrantDependentList<MutableDouble>());
         for (RobotQuadrant quadrantB : RobotQuadrant.values())
         {
            quadrupedDistanceMatrixA.get(quadrantA).set(quadrantB, new MutableDouble(0.0));
            quadrupedDistanceMatrixB.get(quadrantA).set(quadrantB, new MutableDouble(0.0));
         }
      }
      solePointA = new Point3d();
      solePointB = new Point3d();
   }

   public double computeMaxDifference(QuadrantDependentList<Point3d> solePositionsA, QuadrantDependentList<Point3d> solePositionsB)
   {
      setupQuadrupedDistanceMatrix(quadrupedDistanceMatrixA, solePositionsA);
      setupQuadrupedDistanceMatrix(quadrupedDistanceMatrixB, solePositionsB);
      double maxDifference = 0;
      for (RobotQuadrant quadrantA : RobotQuadrant.values())
      {
         for (RobotQuadrant quadrantB : RobotQuadrant.values())
         {
            double currentDifference = Math
                  .abs(quadrupedDistanceMatrixA.get(quadrantA).get(quadrantB).doubleValue() - quadrupedDistanceMatrixB.get(quadrantA).get(quadrantB)
                        .doubleValue());
            if (currentDifference > maxDifference)
            {
               maxDifference = currentDifference;
            }
         }
      }
      return maxDifference;
   }

   public boolean epsilonEquals(QuadrantDependentList<Point3d> solePositionsA, QuadrantDependentList<Point3d> solePositionsB, double epsilon)
   {
      return computeMaxDifference(solePositionsA, solePositionsB) < epsilon;
   }

   private void setupQuadrupedDistanceMatrix(QuadrantDependentList<QuadrantDependentList<MutableDouble>> quadrupedDistanceMatrix,
         QuadrantDependentList<Point3d> quadrupedSolePositionList)
   {
      for (RobotQuadrant quadrantA : RobotQuadrant.values())
      {
         for (RobotQuadrant quadrantB : RobotQuadrant.values())
         {
            solePointA.set(quadrupedSolePositionList.get(quadrantA));
            solePointB.set(quadrupedSolePositionList.get(quadrantB));
            quadrupedDistanceMatrix.get(quadrantA).get(quadrantB).setValue(solePointA.distance(solePointB));
         }
      }
   }
}
