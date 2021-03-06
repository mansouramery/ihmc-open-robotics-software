package us.ihmc.humanoidBehaviors.behaviors;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.vecmath.Point3d;
import javax.vecmath.Point3f;

import georegression.struct.point.Point3D_F64;
import georegression.struct.shapes.Sphere3D_F64;
import us.ihmc.communication.packets.PacketDestination;
import us.ihmc.humanoidBehaviors.communication.ConcurrentListeningQueue;
import us.ihmc.humanoidBehaviors.communication.OutgoingCommunicationBridgeInterface;
import us.ihmc.humanoidRobotics.communication.packets.DetectedObjectPacket;
import us.ihmc.humanoidRobotics.communication.packets.sensing.PointCloudWorldPacket;
import us.ihmc.humanoidRobotics.frames.HumanoidReferenceFrames;
import us.ihmc.robotics.dataStructures.variable.BooleanYoVariable;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.geometry.RigidBodyTransform;
import us.ihmc.sensorProcessing.bubo.clouds.FactoryPointCloudShape;
import us.ihmc.sensorProcessing.bubo.clouds.detect.CloudShapeTypes;
import us.ihmc.sensorProcessing.bubo.clouds.detect.PointCloudShapeFinder;
import us.ihmc.sensorProcessing.bubo.clouds.detect.PointCloudShapeFinder.Shape;
import us.ihmc.sensorProcessing.bubo.clouds.detect.wrapper.ConfigMultiShapeRansac;
import us.ihmc.sensorProcessing.bubo.clouds.detect.wrapper.ConfigSurfaceNormals;
import us.ihmc.tools.io.printing.PrintTools;

public class SphereDetectionBehavior extends BehaviorInterface
{

   private BooleanYoVariable ballFound = new BooleanYoVariable("ballFound", registry);
   private DoubleYoVariable ballRadius = new DoubleYoVariable("ballRadius", registry);
   private DoubleYoVariable ballX = new DoubleYoVariable("ballX", registry);
   private DoubleYoVariable ballY = new DoubleYoVariable("ballY", registry);
   private DoubleYoVariable ballZ = new DoubleYoVariable("ballZ", registry);
   private DoubleYoVariable totalBallsFound = new DoubleYoVariable("totalBallsFound", registry);
   private DoubleYoVariable smallestBallFound = new DoubleYoVariable("smallestBallFound", registry);

   ExecutorService executorService = Executors.newFixedThreadPool(2);
   //   final int pointDropFactor = 4;
   private final static boolean DEBUG = false;

   private final float BALL_RADIUS = 0.0762f;

   protected final ConcurrentListeningQueue<PointCloudWorldPacket> pointCloudQueue = new ConcurrentListeningQueue<PointCloudWorldPacket>();

   private final HumanoidReferenceFrames humanoidReferenceFrames;

   // temp vars
   private final Point3d chestPosition = new Point3d();

   public SphereDetectionBehavior(OutgoingCommunicationBridgeInterface outgoingCommunicationBridge, HumanoidReferenceFrames referenceFrames)
   {
      super(outgoingCommunicationBridge);
      this.attachNetworkProcessorListeningQueue(pointCloudQueue, PointCloudWorldPacket.class);
      this.humanoidReferenceFrames = referenceFrames;
   }

   public boolean foundBall()
   {
      return ballFound.getBooleanValue();
   }

   public void reset()
   {
      ballFound.set(false);
   }

   public double getSpehereRadius()
   {
      return ballRadius.getValueAsDouble();
   }

   public Point3d getBallLocation()
   {
      return new Point3d(ballX.getDoubleValue(), ballY.getDoubleValue(), ballZ.getDoubleValue());
   }

   @Override
   public void doControl()
   {
      if (pointCloudQueue.isNewPacketAvailable())
      {
         findBallsAndSaveResult(pointCloudQueue.getLatestPacket().getDecayingWorldScan());
      }
   }

   protected void findBallsAndSaveResult(Point3f[] points)
   {
      ArrayList<Sphere3D_F64> balls = detectBalls(points);

      totalBallsFound.set(getNumberOfBallsFound());
      smallestBallFound.set(getSmallestRadius());

      int id = 4;
      for (Sphere3D_F64 ball : balls)
      {
         id++;
         RigidBodyTransform t = new RigidBodyTransform();
         t.setTranslation(ball.getCenter().x, ball.getCenter().y, ball.getCenter().z);
         sendPacketToNetworkProcessor(new DetectedObjectPacket(t, 4));
      }

      if (balls.size() > 0)
      {
         ballFound.set(true);
         ballRadius.set(balls.get(0).radius);
         ballX.set(balls.get(0).getCenter().x);
         ballY.set(balls.get(0).getCenter().y);
         ballZ.set(balls.get(0).getCenter().z);
      }
      else
      {
         ballFound.set(false);
         ballRadius.set(0);
         ballX.set(0);
         ballY.set(0);
         ballZ.set(0);
      }
      
      PointCloudWorldPacket pointCloudWorldPacket = new PointCloudWorldPacket();
      pointCloudWorldPacket.setDestination(PacketDestination.UI);
      pointCloudWorldPacket.setTimestamp(System.nanoTime());
      Point3d[] points3d = new Point3d[points.length];
      for (int i = 0; i < points.length; i++)
      {
         points3d[i] = new Point3d(points[i]);
      }
      pointCloudWorldPacket.setDecayingWorldScan(points3d);
      Point3d[] groundQuadTree = new Point3d[1];
      groundQuadTree[0] = new Point3d();
      pointCloudWorldPacket.setGroundQuadTreeSupport(groundQuadTree);
      
      sendPacketToNetworkProcessor(pointCloudWorldPacket);
   }

   public ArrayList<Sphere3D_F64> detectBalls(Point3f[] fullPoints)
   {

      ArrayList<Sphere3D_F64> foundBalls = new ArrayList<Sphere3D_F64>();
      // filter points
      ArrayList<Point3D_F64> pointsNearBy = new ArrayList<Point3D_F64>();
      for (Point3f tmpPoint : fullPoints)
      {
         pointsNearBy.add(new Point3D_F64(tmpPoint.x, tmpPoint.y, tmpPoint.z));
      }

      //    filters =7; angleTolerance =0.9143273078940257; distanceThreashold = 0.08726045545980951; numNeighbors =41; maxDisance = 0.09815802524093345;

      // find plane
      ConfigMultiShapeRansac configRansac = ConfigMultiShapeRansac.createDefault(7, 0.9143273078940257, 0.08726045545980951, CloudShapeTypes.SPHERE);
      configRansac.minimumPoints = 30;
      PointCloudShapeFinder findSpheres = FactoryPointCloudShape.ransacSingleAll(new ConfigSurfaceNormals(41, 0.09815802524093345), configRansac);

      PrintStream out = System.out;
      System.setOut(new PrintStream(new OutputStream()
      {
         @Override
         public void write(int b) throws IOException
         {
         }
      }));
      try
      {
         findSpheres.process(pointsNearBy, null);
      } finally
      {
         System.setOut(out);
      }

      // sort large to small
      humanoidReferenceFrames.getChestFrame().getTransformToWorldFrame().getTranslation(chestPosition);

      final List<Shape> spheres = findSpheres.getFound();
      Collections.sort(spheres, new Comparator<Shape>()
      {
         @Override public int compare(Shape shape0, Shape shape1)
         {
            Sphere3D_F64 sphereParams0 = (Sphere3D_F64) shape0.getParameters();
            Sphere3D_F64 sphereParams1 = (Sphere3D_F64) shape1.getParameters();

            Point3D_F64 center0 = sphereParams0.getCenter();
            Point3D_F64 center1 = sphereParams1.getCenter();

            double distSq0 = (center0.x - chestPosition.x) * (center0.x - chestPosition.x) + (center0.y - chestPosition.y) * (center0.y - chestPosition.y);
            double distSq1 = (center1.x - chestPosition.x) * (center1.x - chestPosition.x) + (center1.y - chestPosition.y) * (center1.y - chestPosition.y);

            return distSq0 < distSq1 ? 1 : -1;
         }
      });

      if (spheres.size() > 0)
      {
         PrintTools.debug(DEBUG, this, "spheres.size() " + spheres.size());
         ballsFound = spheres.size();
         smallestRadius = ((Sphere3D_F64) spheres.get(0).getParameters()).getRadius();
      }
      for (Shape sphere : spheres)
      {
         Sphere3D_F64 sphereParams = (Sphere3D_F64) sphere.getParameters();
         PrintTools.debug(DEBUG, this, "sphere radius" + sphereParams.getRadius() + " center " + sphereParams.getCenter());

         if ((sphereParams.getRadius() < BALL_RADIUS + 0.025f) && (sphereParams.getRadius() > BALL_RADIUS - 0.025f))// soccer ball -
         {
            foundBalls.add(sphereParams);
            PrintTools.debug(DEBUG, this, "------Found Soccer Ball radius" + sphereParams.getRadius() + " center " + sphereParams.getCenter());

            RigidBodyTransform t = new RigidBodyTransform();
            t.setTranslation(sphereParams.getCenter().x, sphereParams.getCenter().y, sphereParams.getCenter().z);
         }

      }
      return foundBalls;

   }

   private double ballsFound = 0;
   private double smallestRadius = 0;

   public double getNumberOfBallsFound()
   {
      return ballsFound;
   }

   public double getSmallestRadius()
   {
      return smallestRadius;
   }

   @Override
   protected void passReceivedNetworkProcessorObjectToChildBehaviors(Object object)
   {
   }

   @Override
   protected void passReceivedControllerObjectToChildBehaviors(Object object)
   {
   }

   @Override
   public void stop()
   {
      defaultStop();
   }

   @Override
   public void enableActions()
   {

   }

   @Override
   public void pause()
   {
      defaultPause();
   }

   @Override
   public void resume()
   {
      defaultResume();
   }

   @Override
   public boolean isDone()
   {
      return ballFound.getBooleanValue();
   }

   @Override
   public void doPostBehaviorCleanup()
   {
      defaultPostBehaviorCleanup();
      ballFound.set(false);
   }

   @Override
   public boolean hasInputBeenSet()
   {
      return true;
   }

   @Override
   public void initialize()
   {
      defaultPostBehaviorCleanup();
   }
}
