package us.ihmc.commonWalkingControlModules.controlModules.foot;

import java.util.List;

import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.CommonOps;

import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.ContactablePlaneBody;
import us.ihmc.commonWalkingControlModules.controlModules.RigidBodySpatialAccelerationControlModule;
import us.ihmc.commonWalkingControlModules.controlModules.foot.FootControlModule.ConstraintType;
import us.ihmc.commonWalkingControlModules.momentumBasedController.MomentumBasedController;
import us.ihmc.commonWalkingControlModules.momentumBasedController.TaskspaceConstraintData;
import us.ihmc.utilities.math.geometry.FrameConvexPolygon2d;
import us.ihmc.utilities.math.geometry.FrameLineSegment2d;
import us.ihmc.utilities.math.geometry.FrameOrientation;
import us.ihmc.utilities.math.geometry.FramePoint;
import us.ihmc.utilities.math.geometry.FramePoint2d;
import us.ihmc.utilities.math.geometry.FrameVector;
import us.ihmc.utilities.math.geometry.ReferenceFrame;
import us.ihmc.utilities.screwTheory.GeometricJacobian;
import us.ihmc.utilities.screwTheory.RigidBody;
import us.ihmc.utilities.screwTheory.SpatialAccelerationVector;
import us.ihmc.utilities.screwTheory.SpatialMotionVector;

import com.yobotics.simulationconstructionset.BooleanYoVariable;
import com.yobotics.simulationconstructionset.DoubleYoVariable;
import com.yobotics.simulationconstructionset.EnumYoVariable;
import com.yobotics.simulationconstructionset.util.GainCalculator;
import com.yobotics.simulationconstructionset.util.math.frames.YoFramePoint;
import com.yobotics.simulationconstructionset.util.math.frames.YoFrameVector;
import com.yobotics.simulationconstructionset.util.statemachines.State;

public abstract class FootControlState extends State<ConstraintType>
{
   protected static final double coefficientOfFriction = 0.8;
   protected static final double minJacobianDeterminant = 0.035;
   protected static final double desiredZAccelerationIntoGround = 0.0;
   protected static final double EPSILON_POINT_ON_EDGE = 1e-2;
   protected static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();
   
   protected final ContactablePlaneBody contactableBody;
   protected final RigidBody rootBody;
   protected final EnumYoVariable<ConstraintType> requestedState;
   
   protected final FramePoint desiredPosition = new FramePoint(worldFrame);
   protected final FrameVector desiredLinearVelocity = new FrameVector(worldFrame);
   protected final FrameVector desiredLinearAcceleration = new FrameVector(worldFrame);
   protected final FrameVector desiredAngularVelocity = new FrameVector(worldFrame);
   protected final FrameVector desiredAngularAcceleration = new FrameVector(worldFrame);
   protected final FrameOrientation desiredOrientation = new FrameOrientation(worldFrame);
   protected final FrameOrientation trajectoryOrientation = new FrameOrientation(worldFrame);
   protected final SpatialAccelerationVector footAcceleration = new SpatialAccelerationVector();
   
   protected final YoFramePoint yoDesiredPosition;
   protected final YoFrameVector yoDesiredLinearVelocity;
   protected final YoFrameVector yoDesiredLinearAcceleration;
   
   protected final RigidBodySpatialAccelerationControlModule accelerationControlModule;
   protected final MomentumBasedController momentumBasedController;
   protected final TaskspaceConstraintData taskspaceConstraintData = new TaskspaceConstraintData();
   protected final int jacobianId;
   
   private final DenseMatrix64F nullspaceMultipliers = new DenseMatrix64F(0, 1);
   private final DoubleYoVariable nullspaceMultiplier;
   private final GeometricJacobian jacobian;
   private final BooleanYoVariable jacobianDeterminantInRange;
   private final BooleanYoVariable doSingularityEscape;
   protected final DenseMatrix64F selectionMatrix;
   protected final BooleanYoVariable forceFootAccelerateIntoGround;
   protected boolean isCoPOnEdge;
   protected FrameLineSegment2d edgeToRotateAbout;
   
   protected final LegSingularityAndKneeCollapseAvoidanceControlModule legSingularityAndKneeCollapseAvoidanceControlModule;
   
   public FootControlState(ConstraintType stateEnum, YoFramePoint yoDesiredPosition,
         YoFrameVector yoDesiredLinearVelocity, YoFrameVector yoDesiredLinearAcceleration,
         RigidBodySpatialAccelerationControlModule accelerationControlModule,
         MomentumBasedController momentumBasedController, ContactablePlaneBody contactableBody,
         EnumYoVariable<ConstraintType> requestedState, int jacobianId,
         DoubleYoVariable nullspaceMultiplier, BooleanYoVariable jacobianDeterminantInRange,
         BooleanYoVariable doSingularityEscape, BooleanYoVariable forceFootAccelerateIntoGround,
         LegSingularityAndKneeCollapseAvoidanceControlModule legSingularityAndKneeCollapseAvoidanceControlModule)
   {
      super(stateEnum);
      
      this.contactableBody = contactableBody;
      this.requestedState = requestedState;
      
      this.yoDesiredPosition = yoDesiredPosition;
      this.yoDesiredLinearVelocity = yoDesiredLinearVelocity;
      this.yoDesiredLinearAcceleration = yoDesiredLinearAcceleration;
      
      this.accelerationControlModule = accelerationControlModule;
      this.momentumBasedController = momentumBasedController;
      this.jacobianId = jacobianId;
      
      this.nullspaceMultiplier = nullspaceMultiplier;
      this.jacobianDeterminantInRange = jacobianDeterminantInRange;
      this.doSingularityEscape = doSingularityEscape;
      this.forceFootAccelerateIntoGround = forceFootAccelerateIntoGround;
      
      this.legSingularityAndKneeCollapseAvoidanceControlModule = legSingularityAndKneeCollapseAvoidanceControlModule;
      
      edgeToRotateAbout = new FrameLineSegment2d(contactableBody.getPlaneFrame());
      rootBody = momentumBasedController.getTwistCalculator().getRootBody();
      taskspaceConstraintData.set(rootBody, contactableBody.getRigidBody());
      jacobian = momentumBasedController.getJacobian(jacobianId);
      
      selectionMatrix = new DenseMatrix64F(SpatialMotionVector.SIZE, SpatialMotionVector.SIZE);
      CommonOps.setIdentity(selectionMatrix);
   }
   
   public abstract void doSpecificAction();
   
   public void doAction()
   {
      legSingularityAndKneeCollapseAvoidanceControlModule.update();
      computeNullspaceMultipliers();
      
      doSpecificAction();
      
      momentumBasedController.setPlaneContactCoefficientOfFriction(contactableBody, coefficientOfFriction);
      
      desiredLinearVelocity.changeFrame(worldFrame);
      yoDesiredLinearVelocity.set(desiredLinearVelocity);
      desiredLinearAcceleration.changeFrame(worldFrame);
      yoDesiredLinearAcceleration.set(desiredLinearAcceleration);
   }
   
   protected void setGains(double kPosition, double kOrientation, double zeta)
   {
      double dPosition = GainCalculator.computeDerivativeGain(kPosition, zeta);
      double dOrientation = GainCalculator.computeDerivativeGain(kOrientation, zeta);
      
      accelerationControlModule.setPositionProportionalGains(kPosition, kPosition, kPosition);
      accelerationControlModule.setPositionDerivativeGains(dPosition, dPosition, dPosition);
      accelerationControlModule.setOrientationProportionalGains(kOrientation, kOrientation, kOrientation);
      accelerationControlModule.setOrientationDerivativeGains(dOrientation, dOrientation, dOrientation);
   }
   
   protected void setTaskspaceConstraint(SpatialAccelerationVector footAcceleration)
   {
      ReferenceFrame bodyFixedFrame = contactableBody.getRigidBody().getBodyFixedFrame();
      footAcceleration.changeBodyFrameNoRelativeAcceleration(bodyFixedFrame);
      footAcceleration.changeFrameNoRelativeMotion(bodyFixedFrame);
      taskspaceConstraintData.set(footAcceleration, nullspaceMultipliers, selectionMatrix);
      momentumBasedController.setDesiredSpatialAcceleration(jacobianId, taskspaceConstraintData);
   }
   
   private void computeNullspaceMultipliers()
   {
      double det = jacobian.det();
      jacobianDeterminantInRange.set(Math.abs(det) < minJacobianDeterminant);

      if (jacobianDeterminantInRange.getBooleanValue())
      {
         nullspaceMultipliers.reshape(1, 1);
         if (doSingularityEscape.getBooleanValue())
         {
            nullspaceMultipliers.set(0, nullspaceMultiplier.getDoubleValue());
         }
         else
         {
            nullspaceMultipliers.set(0, 0);
         }
      }
      else
      {
         nullspaceMultiplier.set(Double.NaN);
         nullspaceMultipliers.reshape(0, 1);
         doSingularityEscape.set(false);
      }
   }
   
   private final FrameConvexPolygon2d contactPolygon = new FrameConvexPolygon2d();
   private final FrameOrientation currentOrientation = new FrameOrientation();
   protected void determineCoPOnEdge()
   {
      FramePoint2d cop = momentumBasedController.getCoP(contactableBody);

      if (cop == null)
      {
         isCoPOnEdge = false;
      }
      else
      {
         List<FramePoint2d> contactPoints = contactableBody.getContactPoints2d();
         contactPolygon.setIncludingFrameAndUpdate(contactPoints);
         cop.changeFrame(contactPolygon.getReferenceFrame());
         FrameLineSegment2d closestEdge = contactPolygon.getClosestEdge(cop);
         boolean copOnEdge = closestEdge.distance(cop) < EPSILON_POINT_ON_EDGE;
         boolean hasCoPBeenOnEdge = isCoPOnEdge;
         if (copOnEdge && !hasCoPBeenOnEdge)
         {
            currentOrientation.set(contactableBody.getBodyFrame());
            currentOrientation.changeFrame(worldFrame);
//            orientationFix.set(currentOrientation);
         }
         isCoPOnEdge = copOnEdge;

         edgeToRotateAbout = closestEdge;
      }
   }
}
