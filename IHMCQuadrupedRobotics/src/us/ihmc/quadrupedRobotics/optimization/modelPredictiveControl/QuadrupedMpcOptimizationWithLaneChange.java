package us.ihmc.quadrupedRobotics.optimization.modelPredictiveControl;

import us.ihmc.quadrupedRobotics.planning.ContactState;
import us.ihmc.quadrupedRobotics.planning.QuadrupedTimedStep;
import us.ihmc.quadrupedRobotics.util.PreallocatedQueue;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.geometry.FrameVector;
import us.ihmc.robotics.robotSide.QuadrantDependentList;

public interface QuadrupedMpcOptimizationWithLaneChange
{
   /**
    * Compute optimal step adjustment and centroidal moment pivot using model predictive control.
    * @param stepAdjustmentVector output step adjustment vector
    * @param cmpPositionSetpoint output center of pressure setpoint
    * @param angularMomentumRate output angular momentum rate of change setpoint
    * @param queuedSteps queue of ongoing and upcoming steps
    * @param currentSolePosition current sole position for each quadrant
    * @param currentContactState current contact state for each quadrant
    * @param currentComPosition current center of mass position
    * @param currentComVelocity current center of mass velocity
    * @param currentTime current time in seconds
    */
   void compute(FrameVector stepAdjustmentVector, FramePoint cmpPositionSetpoint, FrameVector angularMomentumRate, PreallocatedQueue<QuadrupedTimedStep> queuedSteps,
         QuadrantDependentList<FramePoint> currentSolePosition, QuadrantDependentList<ContactState> currentContactState, FramePoint currentComPosition,
         FrameVector currentComVelocity, double currentTime, QuadrupedMpcOptimizationWithLaneChangeSettings settings);
}
