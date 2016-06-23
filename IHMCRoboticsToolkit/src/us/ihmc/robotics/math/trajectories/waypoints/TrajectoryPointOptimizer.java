package us.ihmc.robotics.math.trajectories.waypoints;

import java.util.ArrayList;

import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.CommonOps;

import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.dataStructures.variable.IntegerYoVariable;

/**
 * This class can compute a optimal trajectory from a start point to a target point. Given position and
 * velocity at start and end point as well as waypoint positions this class computes velocities and times
 * at the waypoints such that the integral of the squared acceleration over the whole trajectory is
 * minimized. Time is dimensionless and goes from 0.0 at the start to 1.0 at the target. Optionally the
 * trajectory polynomials between the waypoints can be returned.
 *
 * @author shadylady
 *
 */
public class TrajectoryPointOptimizer
{
   private static final int maxWaypoints = 10;
   private static final double regularizationWeight = 1E-10;
   private static final int maxIterations = 20;
   private static final double epsilon = 1E-7;
   private static final double initialTimeGain = 1E-3;
   private static final double costEpsilon = 0.1;

   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());

   // TODO: clean up this polynomial enum and make it generic
   public enum PolynomialOrder {
         ORDER3,
         ORDER5,
         ORDER7;

      public int getCoefficients()
      {
         switch (this)
         {
         case ORDER3:
            return 4;
         case ORDER5:
            return 6;
         case ORDER7:
            return 8;
         default:
            throw new RuntimeException("Unknown Polynomial Order");
         }
      }

      public void getHBlock(double t0, double t1, DenseMatrix64F hBlockToPack)
      {
         int blockSize = this.getCoefficients() - 2;
         hBlockToPack.reshape(blockSize, blockSize);

         switch (this)
         {
         case ORDER7:
            hBlockToPack.set(blockSize-6, blockSize-6, 1764.0/11.0 * timeDifference(11, t0, t1));
            hBlockToPack.set(blockSize-5, blockSize-6, 126.0       * timeDifference(10, t0, t1));
            hBlockToPack.set(blockSize-4, blockSize-6, 280.0/3.0   * timeDifference(9,  t0, t1));
            hBlockToPack.set(blockSize-3, blockSize-6, 63.0        * timeDifference(8,  t0, t1));
            hBlockToPack.set(blockSize-2, blockSize-6, 36.0        * timeDifference(7,  t0, t1));
            hBlockToPack.set(blockSize-1, blockSize-6, 14.0        * timeDifference(6,  t0, t1));
            hBlockToPack.set(blockSize-5, blockSize-5, 100.0       * timeDifference(9,  t0, t1));
            hBlockToPack.set(blockSize-4, blockSize-5, 75.0        * timeDifference(8,  t0, t1));
            hBlockToPack.set(blockSize-3, blockSize-5, 360.0/7.0   * timeDifference(7,  t0, t1));
            hBlockToPack.set(blockSize-2, blockSize-5, 30.0        * timeDifference(6,  t0, t1));
            hBlockToPack.set(blockSize-1, blockSize-5, 12.0        * timeDifference(5,  t0, t1));
         case ORDER5:
            hBlockToPack.set(blockSize-4, blockSize-4, 400.0/7.0   * timeDifference(7,  t0, t1));
            hBlockToPack.set(blockSize-3, blockSize-4, 40.0        * timeDifference(6,  t0, t1));
            hBlockToPack.set(blockSize-2, blockSize-4, 24.0        * timeDifference(5,  t0, t1));
            hBlockToPack.set(blockSize-1, blockSize-4, 10.0        * timeDifference(4,  t0, t1));
            hBlockToPack.set(blockSize-3, blockSize-3, 144.0/5.0   * timeDifference(5,  t0, t1));
            hBlockToPack.set(blockSize-2, blockSize-3, 18.0        * timeDifference(4,  t0, t1));
            hBlockToPack.set(blockSize-1, blockSize-3, 8.0         * timeDifference(3,  t0, t1));
         case ORDER3:
            hBlockToPack.set(blockSize-2, blockSize-2, 12.0        * timeDifference(3,  t0, t1));
            hBlockToPack.set(blockSize-1, blockSize-2, 6.0         * timeDifference(2,  t0, t1));
            hBlockToPack.set(blockSize-1, blockSize-1, 4.0         * timeDifference(1,  t0, t1));
            break;
         default:
            throw new RuntimeException("Unknown Polynomial Order");
         }

         for (int col = 1; col < blockSize; col++)
         {
            for (int row = 0; row < col; row++)
            {
               hBlockToPack.set(row, col, hBlockToPack.get(col, row));
            }
         }
      }

      private double timeDifference(int power, double t0, double t1)
      {
         return Math.pow(t1, power) - Math.pow(t0, power);
      }

      public boolean getPositionLine(double t, DenseMatrix64F lineToPack)
      {
         lineToPack.reshape(1, getCoefficients());
         switch (this)
         {
         case ORDER7:
            lineToPack.set(0, getCoefficients()-8, 1.0 * Math.pow(t, 7));
            lineToPack.set(0, getCoefficients()-7, 1.0 * Math.pow(t, 6));
         case ORDER5:
            lineToPack.set(0, getCoefficients()-6, 1.0 * Math.pow(t, 5));
            lineToPack.set(0, getCoefficients()-5, 1.0 * Math.pow(t, 4));
         case ORDER3:
            lineToPack.set(0, getCoefficients()-4, 1.0 * Math.pow(t, 3));
            lineToPack.set(0, getCoefficients()-3, 1.0 * Math.pow(t, 2));
            lineToPack.set(0, getCoefficients()-2, 1.0 * t);
            lineToPack.set(0, getCoefficients()-1, 1.0);
            break;
         default:
            throw new RuntimeException("Unknown Polynomial Order");
         }

         return true;
      }

      public boolean getVelocityLine(double t, DenseMatrix64F lineToPack)
      {
         lineToPack.reshape(1, getCoefficients());
         boolean isEndCondition = false;

         switch (this)
         {
         case ORDER7:
            lineToPack.set(0, getCoefficients()-8, 7.0 * Math.pow(t, 6));
            lineToPack.set(0, getCoefficients()-7, 6.0 * Math.pow(t, 5));
         case ORDER5:
            lineToPack.set(0, getCoefficients()-6, 5.0 * Math.pow(t, 4));
            lineToPack.set(0, getCoefficients()-5, 4.0 * Math.pow(t, 3));
         case ORDER3:
            lineToPack.set(0, getCoefficients()-4, 3.0 * Math.pow(t, 2));
            lineToPack.set(0, getCoefficients()-3, 2.0 * t);
            lineToPack.set(0, getCoefficients()-2, 1.0);
            lineToPack.set(0, getCoefficients()-1, 0.0);
            isEndCondition = true;
            break;
         default:
            throw new RuntimeException("Unknown Polynomial Order");
         }

         return isEndCondition;
      }

      public boolean getAccelerationLine(double t, DenseMatrix64F lineToPack)
      {
         lineToPack.reshape(1, getCoefficients());
         boolean isEndCondition = false;

         switch (this)
         {
         case ORDER7:
            lineToPack.set(0, getCoefficients()-8, 7.0 * 6.0 * Math.pow(t, 5));
            lineToPack.set(0, getCoefficients()-7, 6.0 * 5.0 * Math.pow(t, 4));
         case ORDER5:
            lineToPack.set(0, getCoefficients()-6, 5.0 * 4.0 * Math.pow(t, 3));
            lineToPack.set(0, getCoefficients()-5, 4.0 * 3.0 * Math.pow(t, 2));
            isEndCondition = true;
         case ORDER3:
            lineToPack.set(0, getCoefficients()-4, 3.0 * 2.0 * t);
            lineToPack.set(0, getCoefficients()-3, 2.0 * 1.0);
            lineToPack.set(0, getCoefficients()-2, 1.0 * 0.0);
            lineToPack.set(0, getCoefficients()-1, 0.0);
            break;
         default:
            throw new RuntimeException("Unknown Polynomial Order");
         }

         return isEndCondition;
      }

      public boolean getJerkLine(double t, DenseMatrix64F lineToPack)
      {
         lineToPack.reshape(1, getCoefficients());
         boolean isEndCondition = false;

         switch (this)
         {
         case ORDER7:
            lineToPack.set(0, getCoefficients()-8, 7.0 * 6.0 * 5.0 * Math.pow(t, 4));
            lineToPack.set(0, getCoefficients()-7, 6.0 * 5.0 * 4.0 * Math.pow(t, 3));
            isEndCondition = true;
         case ORDER5:
            lineToPack.set(0, getCoefficients()-6, 5.0 * 4.0 * 3.0 * Math.pow(t, 2));
            lineToPack.set(0, getCoefficients()-5, 4.0 * 3.0 * 2.0 * t);
         case ORDER3:
            lineToPack.set(0, getCoefficients()-4, 3.0 * 2.0 * 1.0);
            lineToPack.set(0, getCoefficients()-3, 2.0 * 1.0 * 0.0);
            lineToPack.set(0, getCoefficients()-2, 1.0 * 0.0);
            lineToPack.set(0, getCoefficients()-1, 0.0);
            break;
         default:
            throw new RuntimeException("Unknown Polynomial Order");
         }

         return isEndCondition;
      }
   }

   private final PolynomialOrder order;

   private final IntegerYoVariable dimensions = new IntegerYoVariable("Dimensions", registry);
   private final IntegerYoVariable nWaypoints = new IntegerYoVariable("NumberOfWaypoints", registry);
   private final IntegerYoVariable intervals = new IntegerYoVariable("NumberOfIntervals", registry);
   private final IntegerYoVariable coefficients = new IntegerYoVariable("Coefficients", registry);
   private final IntegerYoVariable problemSize = new IntegerYoVariable("ProblemSize", registry);
   private final IntegerYoVariable constraints = new IntegerYoVariable("Conditions", registry);
   private final IntegerYoVariable iteration = new IntegerYoVariable("Iteration", registry);

   private final DenseMatrix64F x0, x1, xd0, xd1;
   private final ArrayList<DenseMatrix64F> waypoints = new ArrayList<>();

   private final double[] intervalTimes = new double[maxWaypoints+1];
   private final double[] saveIntervalTimes = new double[maxWaypoints+1];
   private final double[] costs = new double[maxIterations+1];

   private final DenseMatrix64F H = new DenseMatrix64F(1, 1);
   private final DenseMatrix64F x = new DenseMatrix64F(1, 1);
   private final DenseMatrix64F f = new DenseMatrix64F(1, 1);
   private final DenseMatrix64F A = new DenseMatrix64F(1, 1);
   private final DenseMatrix64F b = new DenseMatrix64F(1, 1);

   private final DenseMatrix64F E = new DenseMatrix64F(1, 1);
   private final DenseMatrix64F d = new DenseMatrix64F(1, 1);

   private final DenseMatrix64F hBlock = new DenseMatrix64F(1, 1);
   private final DenseMatrix64F Ad = new DenseMatrix64F(1, 1);
   private final DenseMatrix64F bd = new DenseMatrix64F(1, 1);
   private final DenseMatrix64F AdLine = new DenseMatrix64F(1, 1);

   private final DenseMatrix64F timeGradient = new DenseMatrix64F(1, 1);
   private final DoubleYoVariable timeGain = new DoubleYoVariable("TimeGain", registry);

   public TrajectoryPointOptimizer(int dimensions, PolynomialOrder order, YoVariableRegistry parentRegistry)
   {
      this(dimensions, order);
      parentRegistry.addChild(registry);
   }

   public TrajectoryPointOptimizer(int dimensions, PolynomialOrder order)
   {
      dimensions = Math.max(dimensions, 0);
      this.dimensions.set(dimensions);
      this.order = order;
      coefficients.set(order.getCoefficients());
      timeGain.set(initialTimeGain);

      x0 = new DenseMatrix64F(dimensions, 1);
      x1 = new DenseMatrix64F(dimensions, 1);
      xd0 = new DenseMatrix64F(dimensions, 1);
      xd1 = new DenseMatrix64F(dimensions, 1);

      for (int i = 0; i < maxWaypoints; i++)
      {
         waypoints.add(new DenseMatrix64F(dimensions, 1));
      }
   }

   public void setEndPoints(double[] start, double startVel[], double[] target, double[] targetVel)
   {
      if (start.length != dimensions.getIntegerValue())
         throw new RuntimeException("Unexpected Size of Input");
      if (startVel.length != dimensions.getIntegerValue())
         throw new RuntimeException("Unexpected Size of Input");
      if (target.length != dimensions.getIntegerValue())
         throw new RuntimeException("Unexpected Size of Input");
      if (targetVel.length != dimensions.getIntegerValue())
         throw new RuntimeException("Unexpected Size of Input");

      x0.setData(start);
      xd0.setData(startVel);
      x1.setData(target);
      xd1.setData(targetVel);
   }

   public void setWaypoints(ArrayList<double[]> waypoints)
   {
      if (waypoints.size() > maxWaypoints)
         throw new RuntimeException("Too Many Waypoints");
      nWaypoints.set(waypoints.size());

      for (int i = 0; i < nWaypoints.getIntegerValue(); i++)
      {
         if (waypoints.get(i).length != dimensions.getIntegerValue())
            throw new RuntimeException("Unexpected Size of Input");
         this.waypoints.get(i).setData(waypoints.get(i));
      }
   }

   public void compute()
   {
      int intervals = nWaypoints.getIntegerValue() + 1;
      this.intervals.set(intervals);
      for (int i = 0; i < intervals; i++)
      {
         // TODO: use better initial guess:
         // instead of equal time for all intervals use distance between waypoints maybe
         intervalTimes[i] = 1.0 / intervals;
      }

      problemSize.set(dimensions.getIntegerValue() * coefficients.getIntegerValue() * intervals);
      costs[0] = solveMinAcceleration();

      for (int iteration = 0; iteration < maxIterations; iteration++)
      {
         this.iteration.set(iteration);
         double newCost = computeTimeUpdate(costs[iteration]);
         costs[iteration+1] = newCost;

         if (Math.abs(costs[iteration] - newCost) < costEpsilon)
         {
            break;
         }

         if (iteration == maxIterations-1)
         {
            System.err.println("Trajectory optimization max iteration.");
         }
      }
   }

   private double computeTimeUpdate(double cost)
   {
      int intervals = this.intervals.getIntegerValue();
      timeGradient.reshape(intervals, 1);
      System.arraycopy(intervalTimes, 0, saveIntervalTimes, 0, intervalTimes.length);

      for (int i = 0; i < intervals; i++)
      {
         for (int j = 0; j < intervals; j++)
         {
            if (j == i)
            {
               intervalTimes[j] += epsilon;
            }
            else
            {
               intervalTimes[j] -= epsilon / (intervals-1);
            }
         }

         double value = (solveMinAcceleration() - cost) / epsilon;
         timeGradient.set(i, value);

         System.arraycopy(saveIntervalTimes, 0, intervalTimes, 0, saveIntervalTimes.length);
      }

      double length = CommonOps.elementSum(timeGradient);
      CommonOps.add(timeGradient, -length / intervals);
      CommonOps.scale(-timeGain.getDoubleValue(), timeGradient);

      double maxUpdate = CommonOps.elementMaxAbs(timeGradient);
      double minIntervalTime = Double.MAX_VALUE;
      for (int i = 0; i < intervals; i++)
      {
         double intervalTime = intervalTimes[i];
         if (intervalTime < minIntervalTime)
         {
            minIntervalTime = intervalTime;
         }
      }
      if (maxUpdate > 0.4 * minIntervalTime)
      {
         CommonOps.scale(0.4 * minIntervalTime / maxUpdate, timeGradient);
      }

      for (int i = 0; i < intervals; i++)
      {
         intervalTimes[i] += timeGradient.get(i);
      }
      double newCost = solveMinAcceleration();

      if (newCost > cost)
      {
         timeGain.mul(0.5);
         System.arraycopy(saveIntervalTimes, 0, intervalTimes, 0, saveIntervalTimes.length);
         return cost;
      }

      return newCost;
   }

   private double solveMinAcceleration()
   {
      buildCostMatrix();
      buildConstraintMatrices();

      int problemSize = this.problemSize.getIntegerValue();
      f.reshape(problemSize, 1);
      CommonOps.fill(f, regularizationWeight);

      // min 0.5*x'*H*x + f'*x
      // s.t. A*x == b

      int size = problemSize + constraints.getIntegerValue();
      E.reshape(size, size);
      d.reshape(size, 1);

      CommonOps.fill(E, 0.0);
      CommonOps.insert(H, E, 0, 0);
      CommonOps.insert(A, E, problemSize, 0);
      CommonOps.transpose(A);
      CommonOps.insert(A, E, 0, problemSize);
      CommonOps.scale(-1.0, f);
      CommonOps.insert(f, d, 0, 0);
      CommonOps.insert(b, d, problemSize, 0);

      CommonOps.invert(E);
      x.reshape(size, 1);
      CommonOps.mult(E, d, x);
      x.reshape(problemSize, 1);

      d.reshape(problemSize, 1);
      b.reshape(1, 1);
      CommonOps.mult(H, x, d);
      CommonOps.multTransA(x, d, b);
      double cost = 0.5 * b.get(0, 0);
      return cost;
   }

   private void buildConstraintMatrices()
   {
      int dimensions = this.dimensions.getIntegerValue();
      int endpointConstraints = dimensions * order.getCoefficients();
      int waypointConstraints = nWaypoints.getIntegerValue() * dimensions * (2 + order.getCoefficients()/2 - 1);
      constraints.set(endpointConstraints + waypointConstraints);

      int constraints = this.constraints.getIntegerValue();

      A.reshape(constraints, problemSize.getIntegerValue());
      b.reshape(constraints, 1);
      CommonOps.fill(A, 0.0);
      CommonOps.fill(b, 0.0);

      int dimensionConstraints = constraints / dimensions;
      int subProblemSize = problemSize.getIntegerValue() / dimensions;
      Ad.reshape(dimensionConstraints, subProblemSize);
      bd.reshape(dimensionConstraints, 1);

      for (int d = 0; d < dimensions; d++)
      {
         int line = 0;

         if (order.getPositionLine(0.0, AdLine))
         {
            CommonOps.insert(AdLine, Ad, line, 0);
            bd.set(line, x0.get(d));
            line++;
         }
         if (order.getVelocityLine(0.0, AdLine))
         {
            CommonOps.insert(AdLine, Ad, line, 0);
            bd.set(line, xd0.get(d));
            line++;
         }
         if (order.getAccelerationLine(0.0, AdLine))
         {
            CommonOps.insert(AdLine, Ad, line, 0);
            bd.set(line, 0.0);
            line++;
         }
         if (order.getJerkLine(0.0, AdLine))
         {
            CommonOps.insert(AdLine, Ad, line, 0);
            bd.set(line, 0.0);
            line++;
         }

         double t = 0.0;
         for (int w = 0 ; w < nWaypoints.getIntegerValue(); w++)
         {
            t += intervalTimes[w];
            int colOffset = w * order.getCoefficients();
            DenseMatrix64F waypoint = waypoints.get(w);

            order.getPositionLine(t, AdLine);
            CommonOps.insert(AdLine, Ad, line, colOffset);
            bd.set(line, waypoint.get(d));
            line++;
            CommonOps.insert(AdLine, Ad, line, colOffset + order.getCoefficients());
            bd.set(line, waypoint.get(d));
            line++;

            if (order.getVelocityLine(t, AdLine))
            {
               CommonOps.insert(AdLine, Ad, line, colOffset);
               CommonOps.scale(-1.0, AdLine);
               CommonOps.insert(AdLine, Ad, line, colOffset + order.getCoefficients());
               bd.set(line, 0.0);
               line++;
            }

            if (order.getAccelerationLine(t, AdLine))
            {
               CommonOps.insert(AdLine, Ad, line, colOffset);
               CommonOps.scale(-1.0, AdLine);
               CommonOps.insert(AdLine, Ad, line, colOffset + order.getCoefficients());
               bd.set(line, 0.0);
               line++;
            }

            if (order.getJerkLine(t, AdLine))
            {
               CommonOps.insert(AdLine, Ad, line, colOffset);
               CommonOps.scale(-1.0, AdLine);
               CommonOps.insert(AdLine, Ad, line, colOffset + order.getCoefficients());
               bd.set(line, 0.0);
               line++;
            }
         }

         if (order.getPositionLine(1.0, AdLine))
         {
            CommonOps.insert(AdLine, Ad, line, subProblemSize - order.getCoefficients());
            bd.set(line, x1.get(d));
            line++;
         }
         if (order.getVelocityLine(1.0, AdLine))
         {
            CommonOps.insert(AdLine, Ad, line, subProblemSize - order.getCoefficients());
            bd.set(line, xd1.get(d));
            line++;
         }
         if (order.getAccelerationLine(1.0, AdLine))
         {
            CommonOps.insert(AdLine, Ad, line, subProblemSize - order.getCoefficients());
            bd.set(line, 0.0);
            line++;
         }
         if (order.getJerkLine(1.0, AdLine))
         {
            CommonOps.insert(AdLine, Ad, line, subProblemSize - order.getCoefficients());
            bd.set(line, 0.0);
            line++;
         }

         int rowOffset = d * dimensionConstraints;
         int colOffset = d * subProblemSize;
         CommonOps.insert(Ad, A, rowOffset, colOffset);
         CommonOps.insert(bd, b, rowOffset, 0);
      }
   }

   private void buildCostMatrix()
   {
      H.reshape(problemSize.getIntegerValue(), problemSize.getIntegerValue());
      CommonOps.fill(H, 0.0);

      double t0 = 0.0;
      double t1 = intervalTimes[0];
      for (int i = 0; i < intervals.getIntegerValue(); i++)
      {
         order.getHBlock(t0, t1, hBlock);
         for (int d = 0; d < dimensions.getIntegerValue(); d++)
         {
            int offset = (i + d * intervals.getIntegerValue()) * coefficients.getIntegerValue();
            CommonOps.insert(hBlock, H, offset, offset);
         }
         t0 = t1;
         t1 = t1 + intervalTimes[i+1];
      }
   }

   public void getWaypointTimes(double[] timesToPack)
   {
      int n = nWaypoints.getIntegerValue();
      if (timesToPack.length != n)
         throw new RuntimeException("Unexpected Size of Output");
      for (int i = 0; i < n; i++)
      {
         if (i == 0)
         {
            timesToPack[0] = intervalTimes[0];
            continue;
         }
         timesToPack[i] = timesToPack[i-1] + intervalTimes[i];
      }
   }

   public void getPolynomialCoefficients(ArrayList<double[]> coefficientsToPack, int dimension)
   {
      if (coefficientsToPack.size() != intervals.getIntegerValue())
         throw new RuntimeException("Unexpected Size of Output");
      if (dimension > dimensions.getIntegerValue()-1 || dimension < 0)
         throw new RuntimeException("Unknown Dimension");

      for (int i = 0; i < intervals.getIntegerValue(); i++)
      {
         if (coefficientsToPack.get(i).length != order.getCoefficients())
            throw new RuntimeException("Unexpected Size of Output");

         int index = i * order.getCoefficients() + dimension * order.getCoefficients() * intervals.getIntegerValue();
         System.arraycopy(x.data, index, coefficientsToPack.get(i), 0, order.getCoefficients());
      }
   }

}