package us.ihmc.commonWalkingControlModules.controlModules.foot;

import us.ihmc.commonWalkingControlModules.controlModules.foot.FootControlModule.ConstraintType;
import us.ihmc.commonWalkingControlModules.controllerCore.command.feedbackController.FeedbackControlCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseDynamics.InverseDynamicsCommand;
import us.ihmc.commonWalkingControlModules.momentumBasedController.HighLevelHumanoidControllerToolbox;
import us.ihmc.humanoidRobotics.bipedSupportPolygons.ContactableFoot;
import us.ihmc.robotics.geometry.FrameOrientation;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.geometry.FrameVector;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.screwTheory.RigidBody;
import us.ihmc.robotics.screwTheory.SpatialAccelerationVector;
import us.ihmc.robotics.stateMachines.State;

public abstract class AbstractFootControlState extends State<ConstraintType>
{
   protected static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

   protected final FootControlHelper footControlHelper;

   protected final RobotSide robotSide;
   protected final RigidBody rootBody;
   protected final ContactableFoot contactableFoot;

   protected final FramePoint desiredPosition = new FramePoint(worldFrame);
   protected final FrameVector desiredLinearVelocity = new FrameVector(worldFrame);
   protected final FrameVector desiredLinearAcceleration = new FrameVector(worldFrame);
   protected final FrameOrientation desiredOrientation = new FrameOrientation(worldFrame);
   protected final FrameVector desiredAngularVelocity = new FrameVector(worldFrame);
   protected final FrameVector desiredAngularAcceleration = new FrameVector(worldFrame);
   protected final SpatialAccelerationVector footAcceleration = new SpatialAccelerationVector();

   protected final HighLevelHumanoidControllerToolbox momentumBasedController;

   public AbstractFootControlState(ConstraintType stateEnum, FootControlHelper footControlHelper)
   {
      super(stateEnum);

      this.footControlHelper = footControlHelper;
      this.contactableFoot = footControlHelper.getContactableFoot();

      this.momentumBasedController = footControlHelper.getMomentumBasedController();

      this.robotSide = footControlHelper.getRobotSide();

      rootBody = momentumBasedController.getTwistCalculator().getRootBody();
   }

   public abstract void doSpecificAction();

   public abstract InverseDynamicsCommand<?> getInverseDynamicsCommand();

   public abstract FeedbackControlCommand<?> getFeedbackControlCommand();

   @Override
   public void doAction()
   {
      doSpecificAction();
   }

   @Override
   public void doTransitionIntoAction()
   {
   }

   @Override
   public void doTransitionOutOfAction()
   {
   }
}
