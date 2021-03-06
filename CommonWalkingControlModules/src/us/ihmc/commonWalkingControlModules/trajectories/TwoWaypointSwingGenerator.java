package us.ihmc.commonWalkingControlModules.trajectories;

import java.util.ArrayList;

import us.ihmc.graphics3DAdapter.graphics.appearances.YoAppearance;
import us.ihmc.robotics.MathTools;
import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.dataStructures.variable.BooleanYoVariable;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.dataStructures.variable.EnumYoVariable;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.geometry.FrameVector;
import us.ihmc.robotics.math.trajectories.PositionTrajectoryGenerator;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.trajectories.TrajectoryType;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.BagOfBalls;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicsListRegistry;

public class TwoWaypointSwingGenerator implements PositionTrajectoryGenerator
{
   private static final int maxTimeIterations = 1;
   private static final int numberWaypoints = 2;
   private static final double[] waypointProportions = new double[] {0.15, 0.85};
   private static final double defaultSwingHeight = 0.1;
   private static final double minSwingHeight = 0.1;

   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

   private final YoVariableRegistry registry;

   private final DoubleYoVariable stepTime;
   private final DoubleYoVariable timeIntoStep;
   private final BooleanYoVariable isDone;
   private final EnumYoVariable<TrajectoryType> trajectoryType;
   private final DoubleYoVariable swingHeight;
   private final DoubleYoVariable maxSwingHeight;

   private final Position2dOptimizedSwingTrajectoryGenerator trajectory;

   private final FramePoint initialPosition = new FramePoint();
   private final FrameVector initialVelocity = new FrameVector();
   private final FramePoint finalPosition = new FramePoint();
   private final FrameVector finalVelocity = new FrameVector();
   private final ArrayList<FramePoint> waypointPositions = new ArrayList<>();
   private final FramePoint stanceFootPosition = new FramePoint();

   private final BagOfBalls waypointViz;

   public TwoWaypointSwingGenerator(String namePrefix, double maxSwingHeight, YoVariableRegistry parentRegistry, YoGraphicsListRegistry yoGraphicsListRegistry)
   {
      registry = new YoVariableRegistry(namePrefix + getClass().getSimpleName());
      parentRegistry.addChild(registry);

      stepTime = new DoubleYoVariable(namePrefix + "StepTime", registry);
      timeIntoStep = new DoubleYoVariable(namePrefix + "TimeIntoStep", registry);
      isDone = new BooleanYoVariable(namePrefix + "IsDone", registry);
      trajectoryType = new EnumYoVariable<>(namePrefix + "TrajectoryType", registry, TrajectoryType.class);
      swingHeight = new DoubleYoVariable(namePrefix + "SwingHeight", registry);
      swingHeight.set(defaultSwingHeight);

      this.maxSwingHeight = new DoubleYoVariable(namePrefix + "MaxSwingHeight", registry);
      this.maxSwingHeight.set(maxSwingHeight);

      trajectory = new Position2dOptimizedSwingTrajectoryGenerator(namePrefix, registry, yoGraphicsListRegistry, maxTimeIterations);

      for (int i = 0; i < numberWaypoints; i++)
         waypointPositions.add(new FramePoint());

      if (yoGraphicsListRegistry != null)
         waypointViz = new BagOfBalls(numberWaypoints, 0.02, namePrefix + "Waypoints", YoAppearance.White(), registry, yoGraphicsListRegistry);
      else
         waypointViz = null;
   }

   public void setStepTime(double stepTime)
   {
      this.stepTime.set(stepTime);
   }

   public void setInitialConditions(FramePoint initialPosition, FrameVector initialVelocity)
   {
      this.initialPosition.setIncludingFrame(initialPosition);
      this.initialVelocity.setIncludingFrame(initialVelocity);
   }

   public void setFinalConditions(FramePoint finalPosition, FrameVector finalVelocity)
   {
      this.finalPosition.setIncludingFrame(finalPosition);
      this.finalVelocity.setIncludingFrame(finalVelocity);
   }

   public void setTrajectoryType(TrajectoryType trajectoryType)
   {
      this.trajectoryType.set(trajectoryType);
   }

   public void setSwingHeight(double swingHeight)
   {
      if (Double.isNaN(swingHeight))
         this.swingHeight.set(defaultSwingHeight);
      else if (swingHeight < minSwingHeight)
         this.swingHeight.set(minSwingHeight);
      else if (swingHeight > maxSwingHeight.getDoubleValue())
         this.swingHeight.set(maxSwingHeight.getDoubleValue());
      else
         this.swingHeight.set(swingHeight);
   }

   public void setStanceFootPosition(FramePoint stanceFootPosition)
   {
      this.stanceFootPosition.setIncludingFrame(stanceFootPosition);
   }

   public void informDone()
   {
      trajectory.informDone();
   }

   @Override
   public void initialize()
   {
      timeIntoStep.set(0.0);
      isDone.set(false);

      initialPosition.changeFrame(worldFrame);
      finalPosition.changeFrame(worldFrame);
      double maxStepZ = Math.max(initialPosition.getZ(), finalPosition.getZ());

      switch (trajectoryType.getEnumValue())
      {
      case OBSTACLE_CLEARANCE:
         for (int i = 0; i < numberWaypoints; i++)
         {
            waypointPositions.get(i).interpolate(initialPosition, finalPosition, waypointProportions[i]);
            waypointPositions.get(i).setZ(maxStepZ + swingHeight.getDoubleValue());
         }
         break;
      case PUSH_RECOVERY:
      case BASIC:
      case DEFAULT:
         for (int i = 0; i < numberWaypoints; i++)
         {
            waypointPositions.get(i).interpolate(initialPosition, finalPosition, waypointProportions[i]);
            waypointPositions.get(i).add(0.0, 0.0, swingHeight.getDoubleValue());
         }
         break;
      default:
         throw new RuntimeException("Trajectory type not implemented");
      }

      stanceFootPosition.changeFrame(worldFrame);
      double maxWaypointZ = Math.max(stanceFootPosition.getZ() + maxSwingHeight.getDoubleValue(), maxStepZ + defaultSwingHeight);
      for (int i = 0; i < numberWaypoints; i++)
      {
         waypointPositions.get(i).setZ(Math.min(waypointPositions.get(i).getZ(), maxWaypointZ));
      }

      trajectory.setEndpointConditions(initialPosition, initialVelocity, finalPosition, finalVelocity);
      trajectory.setWaypoints(waypointPositions);
      trajectory.initialize();

      visualize();
   }

   private void visualize()
   {
      if (waypointViz == null)
         return;

      for (int i = 0; i < numberWaypoints; i++)
         waypointViz.setBall(waypointPositions.get(i), i);
   }

   @Override
   public void compute(double time)
   {
      double trajectoryTime = stepTime.getDoubleValue();
      isDone.set(time >= trajectoryTime);

      time = MathTools.clipToMinMax(time, 0.0, trajectoryTime);
      timeIntoStep.set(time);

      double percent = time / trajectoryTime;
      trajectory.compute(percent);
   }

   @Override
   public boolean isDone()
   {
      return isDone.getBooleanValue();
   }

   @Override
   public void getPosition(FramePoint positionToPack)
   {
      trajectory.getPosition(positionToPack);
   }

   @Override
   public void getVelocity(FrameVector velocityToPack)
   {
      trajectory.getVelocity(velocityToPack);
   }

   @Override
   public void getAcceleration(FrameVector accelerationToPack)
   {
      trajectory.getAcceleration(accelerationToPack);
   }

   @Override
   public void getLinearData(FramePoint positionToPack, FrameVector velocityToPack, FrameVector accelerationToPack)
   {
      getPosition(positionToPack);
      getVelocity(velocityToPack);
      getAcceleration(accelerationToPack);
   }

   @Override
   public void showVisualization()
   {
      // TODO Auto-generated method stub

   }

   @Override
   public void hideVisualization()
   {
      // TODO Auto-generated method stub

   }

}