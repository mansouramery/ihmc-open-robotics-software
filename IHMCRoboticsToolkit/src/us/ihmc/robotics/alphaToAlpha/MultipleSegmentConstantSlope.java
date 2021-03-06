package us.ihmc.robotics.alphaToAlpha;

import javax.vecmath.Point2d;

import us.ihmc.robotics.alphaToAlpha.AlphaToAlphaFunction;

@SuppressWarnings("all") public class MultipleSegmentConstantSlope implements AlphaToAlphaFunction
{
   private Point2d[] segmentPoints;

   public MultipleSegmentConstantSlope(Point2d[] segmentPoints)
   {
      // slope betwen points must be positive
      for (int i = 0; i < segmentPoints.length - 1; i++)
      {
         double xDifference = segmentPoints[i + 1].x - segmentPoints[i].x;
         double yDifference = segmentPoints[i + 1].y - segmentPoints[i].y;
         if ((xDifference <= 0.0) || (yDifference < 0.0))
         {
            throw new RuntimeException("Slope of line must be greater than zero");
         }
      }

      this.segmentPoints = segmentPoints;
   }

   public double getAlphaPrime(double alpha)
   {
      if (alpha <= 0.0)
         return 0.0;

      for (int i = 0; i < segmentPoints.length - 1; i++)
      {
         if (alpha < segmentPoints[i + 1].x)
         {
            double x0 = segmentPoints[i].x;
            double y0 = segmentPoints[i].y;
            double x1 = segmentPoints[i + 1].x;
            double y1 = segmentPoints[i + 1].y;

            double alphaPrime = y0 + (alpha - x0) / (x1 - x0) * (y1 - y0);

            return alphaPrime;
         }
      }

      return segmentPoints[segmentPoints.length - 1].y;
   }

   public static void main(String[] args)
   {
      //    Point2d[] listOfPoints = new Point2d[]{
      //        new Point2d(0.2, 0.2),
      //        new Point2d(0.4, 0.7),
      //        new Point2d(0.5, 1.0),
      //    };

      Point2d[] listOfPoints = new Point2d[] {new Point2d(0.0, 0.0), new Point2d(0.5, 0.0), new Point2d(2.0, 1.0)};

      MultipleSegmentConstantSlope multipleSegmentConstantSlope = new MultipleSegmentConstantSlope(listOfPoints);

      for (double i = -1.0; i <= 3.0; i = i + 0.1)
      {
         System.out.println("alpha=" + i + ", alpha prime=" + multipleSegmentConstantSlope.getAlphaPrime(i));
      }

   }

   public double getMaxAlpha()
   {
      Point2d lastPoint = this.segmentPoints[segmentPoints.length];

      return lastPoint.x;
   }

   @Override public double getDerivativeAtAlpha(double alpha)
   {

      return 0;
   }

   @Override public double getSecondDerivativeAtAlpha(double alpha)
   {

      return 0;
   }
}
