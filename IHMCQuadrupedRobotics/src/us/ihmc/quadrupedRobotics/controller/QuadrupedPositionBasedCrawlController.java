package us.ihmc.quadrupedRobotics.controller;

import java.awt.Color;

import javax.vecmath.Point2d;
import javax.vecmath.Vector3d;

import us.ihmc.SdfLoader.SDFFullRobotModel;
import us.ihmc.convexOptimization.qpOASES.returnValue;
import us.ihmc.graphics3DAdapter.graphics.appearances.AppearanceDefinition;
import us.ihmc.graphics3DAdapter.graphics.appearances.YoAppearance;
import us.ihmc.quadrupedRobotics.footstepChooser.MidFootZUpSwingTargetGenerator;
import us.ihmc.quadrupedRobotics.footstepChooser.QuadrupedControllerParameters;
import us.ihmc.quadrupedRobotics.inverseKinematics.QuadrupedLegInverseKinematicsCalculator;
import us.ihmc.quadrupedRobotics.parameters.QuadrupedJointNameMap;
import us.ihmc.quadrupedRobotics.parameters.QuadrupedRobotParameters;
import us.ihmc.quadrupedRobotics.referenceFrames.QuadrupedReferenceFrames;
import us.ihmc.quadrupedRobotics.supportPolygon.QuadrupedSupportPolygon;
import us.ihmc.quadrupedRobotics.trajectory.QuadrupedSwingTrajectoryGenerator;
import us.ihmc.robotics.dataStructures.listener.VariableChangedListener;
import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.dataStructures.variable.BooleanYoVariable;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.dataStructures.variable.EnumYoVariable;
import us.ihmc.robotics.dataStructures.variable.YoVariable;
import us.ihmc.robotics.geometry.ConvexPolygon2d;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.geometry.FramePoint2d;
import us.ihmc.robotics.geometry.FramePose;
import us.ihmc.robotics.geometry.FrameVector;
import us.ihmc.robotics.math.filters.AlphaFilteredWrappingYoVariable;
import us.ihmc.robotics.math.filters.AlphaFilteredYoFramePoint;
import us.ihmc.robotics.math.filters.AlphaFilteredYoVariable;
import us.ihmc.robotics.math.frames.YoFrameConvexPolygon2d;
import us.ihmc.robotics.math.frames.YoFrameLineSegment2d;
import us.ihmc.robotics.math.frames.YoFrameOrientation;
import us.ihmc.robotics.math.frames.YoFramePoint;
import us.ihmc.robotics.math.frames.YoFramePose;
import us.ihmc.robotics.math.frames.YoFrameVector;
import us.ihmc.robotics.math.trajectories.StraightLinePositionTrajectoryGenerator;
import us.ihmc.robotics.math.trajectories.providers.YoPositionProvider;
import us.ihmc.robotics.math.trajectories.providers.YoVariableDoubleProvider;
import us.ihmc.robotics.referenceFrames.PoseReferenceFrame;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.robotSide.QuadrantDependentList;
import us.ihmc.robotics.robotSide.RobotQuadrant;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.screwTheory.CenterOfMassJacobian;
import us.ihmc.robotics.stateMachines.State;
import us.ihmc.robotics.stateMachines.StateMachine;
import us.ihmc.robotics.stateMachines.StateTransition;
import us.ihmc.robotics.stateMachines.StateTransitionCondition;
import us.ihmc.simulationconstructionset.robotController.RobotController;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicPosition;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicPosition.GraphicType;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicReferenceFrame;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicsListRegistry;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.plotting.YoArtifactCircle;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.plotting.YoArtifactLineSegment2d;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.plotting.YoArtifactPolygon;

public class QuadrupedPositionBasedCrawlController implements RobotController
{
   private static final double INITIAL_DESIRED_FOOT_CORRECTION_BREAK_FREQUENCY = 1.0;
   private static final double DEFAULT_DESIRED_FOOT_CORRECTION_BREAK_FREQUENCY = 0.15;
   private static final double DEFAULT_HEADING_CORRECTION_BREAK_FREQUENCY = 1.0;
   private static final double DEFAULT_COM_PITCH_FILTER_BREAK_FREQUENCY = 0.75;
   private static final double DEFAULT_COM_ROLL_FILTER_BREAK_FREQUENCY = 0.75;
   private static final double DEFAULT_COM_HEIGHT_Z_FILTER_BREAK_FREQUENCY = 0.6;
   
   
   
   private final double dt;
   private final String name = getClass().getSimpleName();
   private final YoVariableRegistry registry = new YoVariableRegistry(name);
   private final DoubleYoVariable robotTimestamp;
   
   public enum CrawlGateWalkingState
   {
      QUADRUPLE_SUPPORT, TRIPLE_SUPPORT
   }

   
   private final StateMachine<CrawlGateWalkingState> walkingStateMachine;
   private final QuadrupedLegInverseKinematicsCalculator inverseKinematicsCalculators;
   
   private final SDFFullRobotModel fullRobotModel;
   private final QuadrupedReferenceFrames referenceFrames;
   private final CenterOfMassJacobian centerOfMassJacobian;
   private final ReferenceFrame bodyFrame;
   private final ReferenceFrame comFrame;
   private final PoseReferenceFrame desiredCoMPoseReferenceFrame = new PoseReferenceFrame("desiredCoMPoseReferenceFrame", ReferenceFrame.getWorldFrame());
   
   private final DoubleYoVariable filteredDesiredCoMYawAlphaBreakFrequency = new DoubleYoVariable("filteredDesiredCoMYawAlphaBreakFrequency", registry);
   private final DoubleYoVariable filteredDesiredCoMYawAlpha = new DoubleYoVariable("filteredDesiredCoMYawAlpha", registry);
   
   private final DoubleYoVariable filteredDesiredCoMPitchAlphaBreakFrequency = new DoubleYoVariable("filteredDesiredCoMPitchAlphaBreakFrequency", registry);
   private final DoubleYoVariable filteredDesiredCoMPitchAlpha = new DoubleYoVariable("filteredDesiredCoMOrientationAlpha", registry);
   
   private final DoubleYoVariable filteredDesiredCoMRollAlphaBreakFrequency = new DoubleYoVariable("filteredDesiredCoMRollAlphaBreakFrequency", registry);
   private final DoubleYoVariable filteredDesiredCoMRollAlpha = new DoubleYoVariable("filteredDesiredCoMRollAlpha", registry);

   private final YoFramePoint desiredCoMPosition = new YoFramePoint("desiredCoMPosition", ReferenceFrame.getWorldFrame(), registry);

   private final DoubleYoVariable desiredCoMHeight = new DoubleYoVariable("desiredCoMHeight", registry);
   private final DoubleYoVariable filteredDesiredCoMHeightAlphaBreakFrequency = new DoubleYoVariable("filteredDesiredCoMHeightAlphaBreakFrequency", registry);
   private final DoubleYoVariable filteredDesiredCoMHeightAlpha = new DoubleYoVariable("filteredDesiredCoMHeightAlpha", registry);
   private final AlphaFilteredYoVariable filteredDesiredCoMHeight = new AlphaFilteredYoVariable("filteredDesiredCoMHeight", registry, filteredDesiredCoMHeightAlpha , desiredCoMHeight );
   
   private final YoFrameOrientation desiredCoMOrientation = new YoFrameOrientation("desiredCoMOrientation", ReferenceFrame.getWorldFrame(), registry);
   private final AlphaFilteredWrappingYoVariable filteredDesiredCoMYaw = new AlphaFilteredWrappingYoVariable("filteredDesiredCoMYaw", "", registry, desiredCoMOrientation.getYaw(), filteredDesiredCoMYawAlpha, -Math.PI, Math.PI);
   private final AlphaFilteredWrappingYoVariable filteredDesiredCoMPitch = new AlphaFilteredWrappingYoVariable("filteredDesiredCoMPitch", "", registry, desiredCoMOrientation.getPitch(), filteredDesiredCoMPitchAlpha, -Math.PI, Math.PI);
   private final AlphaFilteredWrappingYoVariable filteredDesiredCoMRoll = new AlphaFilteredWrappingYoVariable("filteredDesiredCoMRoll", "", registry, desiredCoMOrientation.getRoll(), filteredDesiredCoMRollAlpha, -Math.PI, Math.PI);
   private final YoFrameOrientation filteredDesiredCoMOrientation = new YoFrameOrientation(filteredDesiredCoMYaw, filteredDesiredCoMPitch, filteredDesiredCoMRoll, ReferenceFrame.getWorldFrame());
   private final YoFramePose desiredCoMPose = new YoFramePose(desiredCoMPosition, filteredDesiredCoMOrientation);

   private final EnumYoVariable<RobotQuadrant> swingLeg = new EnumYoVariable<RobotQuadrant>("swingLeg", registry, RobotQuadrant.class, true);
   private final YoFrameVector desiredVelocity;
   private final DoubleYoVariable desiredYawRate = new DoubleYoVariable("desiredYawRate", registry);

   private final DoubleYoVariable nominalYaw = new DoubleYoVariable("nominalYaw", registry);
   private final YoFrameLineSegment2d nominalYawLineSegment = new YoFrameLineSegment2d("nominalYawLineSegment", "", ReferenceFrame.getWorldFrame(), registry);
   private final YoArtifactLineSegment2d nominalYawArtifact = new YoArtifactLineSegment2d("nominalYawArtifact", nominalYawLineSegment, Color.YELLOW, 0.02, 0.02);
   private final FramePoint2d endPoint2d = new FramePoint2d();
   
   private final QuadrupedSupportPolygon fourFootSupportPolygon = new QuadrupedSupportPolygon();
   private final QuadrupedSupportPolygon commonSupportPolygon = new QuadrupedSupportPolygon();
   
   private final QuadrantDependentList<QuadrupedSwingTrajectoryGenerator> swingTrajectoryGenerators = new QuadrantDependentList<>();
   private final DoubleYoVariable swingDuration = new DoubleYoVariable("swingDuration", registry);
   
   private enum SwingTargetGeneratorType {MIDZUP, INPLACE};
   private final EnumYoVariable<SwingTargetGeneratorType> selectedSwingTargetGenerator;
   private final MidFootZUpSwingTargetGenerator zUpSwingTargetGenerator;
   
   private final QuadrantDependentList<ReferenceFrame> legAttachmentFrames = new QuadrantDependentList<>();
   private final QuadrantDependentList<YoFramePoint> actualFeetLocations = new QuadrantDependentList<YoFramePoint>();
   private final QuadrantDependentList<AlphaFilteredYoFramePoint> desiredFeetLocations = new QuadrantDependentList<AlphaFilteredYoFramePoint>();
   private final QuadrantDependentList<DoubleYoVariable> desiredFeetLocationsAlpha = new QuadrantDependentList<DoubleYoVariable>();
   private final DoubleYoVariable desiredFeetAlphaFilterBreakFrequency = new DoubleYoVariable("desiredFeetAlphaFilterBreakFrequency", registry);
   private final BooleanYoVariable enableFootAlpha = new BooleanYoVariable("enableFootAlpha", registry);
   
   private final YoFrameConvexPolygon2d supportPolygon = new YoFrameConvexPolygon2d("quadPolygon", "", ReferenceFrame.getWorldFrame(), 4, registry);
   private final YoFrameConvexPolygon2d currentTriplePolygon = new YoFrameConvexPolygon2d("currentTriplePolygon", "", ReferenceFrame.getWorldFrame(), 3, registry);
   private final YoFrameConvexPolygon2d upcommingTriplePolygon = new YoFrameConvexPolygon2d("upcommingTriplePolygon", "", ReferenceFrame.getWorldFrame(), 3, registry);
   private final YoFrameConvexPolygon2d commonTriplePolygon = new YoFrameConvexPolygon2d("commonTriplePolygon", "", ReferenceFrame.getWorldFrame(), 3, registry);
   
   private final YoFramePoint circleCenter = new YoFramePoint("circleCenter", ReferenceFrame.getWorldFrame(), registry);
   private final Point2d circleCenter2d = new Point2d();
   private final YoGraphicPosition circleCenterGraphic = new YoGraphicPosition("circleCenterGraphic", circleCenter, 0.005, YoAppearance.Green());

   private final DoubleYoVariable inscribedCircleRadius = new DoubleYoVariable("inscribedCircleRadius", registry);
   private final YoArtifactCircle inscribedCircle = new YoArtifactCircle("inscribedCircle", circleCenter, inscribedCircleRadius, Color.BLACK);
   
   private final BooleanYoVariable useSubCircleForBodyShiftTarget = new BooleanYoVariable("useSubCircleForBodyShiftTarget", registry);
   private final DoubleYoVariable subCircleRadius = new DoubleYoVariable("subCircleRadius", registry);
   
   private final YoFramePoint centerOfMassPosition = new YoFramePoint("centerOfMass", ReferenceFrame.getWorldFrame(), registry);
   private final FramePoint centerOfMassFramePoint = new FramePoint();
   private final Point2d centerOfMassPoint2d = new Point2d();
   private final YoGraphicPosition centerOfMassViz = new YoGraphicPosition("centerOfMass", centerOfMassPosition, 0.02, YoAppearance.Black(), GraphicType.BALL_WITH_CROSS);
   
   private final YoFramePoint currentSwingTarget = new YoFramePoint("currentSwingTarget", ReferenceFrame.getWorldFrame(), registry);
   private final YoGraphicPosition currentSwingTargetViz = new YoGraphicPosition("currentSwingTarget", currentSwingTarget, 0.01, YoAppearance.Red());
   
   private final YoFramePoint desiredCoMTarget = new YoFramePoint("desiredCoMTarget", ReferenceFrame.getWorldFrame(), registry);
   private final YoGraphicPosition desiredCoMTargetViz = new YoGraphicPosition("desiredCoMTargetViz", desiredCoMTarget, 0.01, YoAppearance.Turquoise());
   
   private final YoFramePoint desiredCoM = new YoFramePoint("desiredCoM", ReferenceFrame.getWorldFrame(), registry);
   private final YoGraphicPosition desiredCoMViz = new YoGraphicPosition("desiredCoMViz", desiredCoM, 0.01, YoAppearance.HotPink());
   
   private final YoFramePoint currentICP = new YoFramePoint("currentICP", ReferenceFrame.getWorldFrame(), registry);
   private final YoGraphicPosition currentICPViz = new YoGraphicPosition("currentICPViz", currentICP, 0.01, YoAppearance.DarkSlateBlue());
   
   private final YoGraphicReferenceFrame desiredCoMPoseYoGraphic = new YoGraphicReferenceFrame(desiredCoMPoseReferenceFrame, registry, 0.45);
   private final YoGraphicReferenceFrame comPoseYoGraphic;
   private final YoGraphicReferenceFrame leftMidZUpFrameViz;
   private final YoGraphicReferenceFrame rightMidZUpFrameViz;
   
   /** body sway trajectory **/
   private final StraightLinePositionTrajectoryGenerator bodyTrajectoryGenerator;
   private final YoFramePoint initialCoMPosition = new YoFramePoint("initialBodyPosition", ReferenceFrame.getWorldFrame(), registry);
   private final DoubleYoVariable bodyMovementTrajectoryTimeStart = new DoubleYoVariable("bodyMovementTrajectoryTimeStart", registry);
   private final DoubleYoVariable bodyMovementTrajectoryTimeCurrent = new DoubleYoVariable("bodyMovementTrajectoryTimeCurrent", registry);
   private final DoubleYoVariable bodyMovementTrajectoryTimeDesired = new DoubleYoVariable("bodyMovementTrajectoryTimeDesired", registry);
   private final YoVariableDoubleProvider trajectoryTimeProvider =new YoVariableDoubleProvider(bodyMovementTrajectoryTimeDesired);
   private final YoPositionProvider initialBodyPositionProvider = new YoPositionProvider(initialCoMPosition);
   private final YoPositionProvider finalBodyPositionProvider = new YoPositionProvider(desiredCoMTarget);
   
   public QuadrupedPositionBasedCrawlController(final double dt, QuadrupedRobotParameters robotParameters, SDFFullRobotModel fullRobotModel, QuadrupedJointNameMap quadrupedJointNameMap,
         final QuadrupedReferenceFrames referenceFrames, QuadrupedLegInverseKinematicsCalculator quadrupedInverseKinematicsCalulcator, YoGraphicsListRegistry yoGraphicsListRegistry,
         DoubleYoVariable yoTime)
   {
      swingDuration.set(0.3);
      subCircleRadius.set(0.1);
      useSubCircleForBodyShiftTarget.set(true);
      swingLeg.set(RobotQuadrant.FRONT_RIGHT);
      
      this.robotTimestamp = yoTime;
      this.dt = dt;
      this.referenceFrames = referenceFrames;
      this.fullRobotModel = fullRobotModel;
      this.centerOfMassJacobian = new CenterOfMassJacobian(fullRobotModel.getElevator());
      this.walkingStateMachine = new StateMachine<CrawlGateWalkingState>(name, "walkingStateTranistionTime", CrawlGateWalkingState.class, yoTime, registry);
      inverseKinematicsCalculators = quadrupedInverseKinematicsCalulcator;
      
      QuadrupedControllerParameters quadrupedControllerParameters = robotParameters.getQuadrupedControllerParameters();
      referenceFrames.updateFrames();
      bodyFrame = referenceFrames.getBodyFrame();
      comFrame = referenceFrames.getCenterOfMassFrame();
      
      desiredVelocity = new YoFrameVector("desiredVelocity", bodyFrame, registry);
      desiredVelocity.setX(0.0);
      bodyMovementTrajectoryTimeDesired.set(1.0);
      
      bodyTrajectoryGenerator = new StraightLinePositionTrajectoryGenerator("body", ReferenceFrame.getWorldFrame(), trajectoryTimeProvider, initialBodyPositionProvider, finalBodyPositionProvider, registry);
      
      selectedSwingTargetGenerator = new EnumYoVariable<QuadrupedPositionBasedCrawlController.SwingTargetGeneratorType>("selectedSwingTargetGenerator", registry, SwingTargetGeneratorType.class);
      selectedSwingTargetGenerator.set(SwingTargetGeneratorType.MIDZUP);
      zUpSwingTargetGenerator = new MidFootZUpSwingTargetGenerator(quadrupedControllerParameters, referenceFrames, registry);
      
      comPoseYoGraphic = new YoGraphicReferenceFrame("rasta_", referenceFrames.getCenterOfMassFrame(), registry, 0.25, YoAppearance.Green());
      leftMidZUpFrameViz = new YoGraphicReferenceFrame(referenceFrames.getSideDependentMidFeetZUpFrame(RobotSide.LEFT), registry, 0.2);
      rightMidZUpFrameViz = new YoGraphicReferenceFrame(referenceFrames.getSideDependentMidFeetZUpFrame(RobotSide.RIGHT), registry, 0.2);
      
      filteredDesiredCoMYawAlphaBreakFrequency.set(DEFAULT_HEADING_CORRECTION_BREAK_FREQUENCY);
      filteredDesiredCoMYawAlpha.set(AlphaFilteredYoVariable.computeAlphaGivenBreakFrequencyProperly(filteredDesiredCoMYawAlphaBreakFrequency.getDoubleValue(), dt));
      filteredDesiredCoMYawAlphaBreakFrequency.addVariableChangedListener(createBreakFrequencyChangeListener(dt, filteredDesiredCoMYawAlphaBreakFrequency, filteredDesiredCoMYawAlpha));
      
      filteredDesiredCoMPitchAlphaBreakFrequency.set(DEFAULT_COM_PITCH_FILTER_BREAK_FREQUENCY);
      filteredDesiredCoMPitchAlpha.set(AlphaFilteredYoVariable.computeAlphaGivenBreakFrequencyProperly(filteredDesiredCoMPitchAlphaBreakFrequency.getDoubleValue(), dt));
      filteredDesiredCoMPitchAlphaBreakFrequency.addVariableChangedListener(createBreakFrequencyChangeListener(dt, filteredDesiredCoMPitchAlphaBreakFrequency, filteredDesiredCoMPitchAlpha));
      
      filteredDesiredCoMRollAlphaBreakFrequency.set(DEFAULT_COM_ROLL_FILTER_BREAK_FREQUENCY);
      filteredDesiredCoMRollAlpha.set(AlphaFilteredYoVariable.computeAlphaGivenBreakFrequencyProperly(filteredDesiredCoMRollAlphaBreakFrequency.getDoubleValue(), dt));
      filteredDesiredCoMRollAlphaBreakFrequency.addVariableChangedListener(createBreakFrequencyChangeListener(dt, filteredDesiredCoMRollAlphaBreakFrequency, filteredDesiredCoMRollAlpha));
      
      filteredDesiredCoMHeightAlphaBreakFrequency.set(DEFAULT_COM_HEIGHT_Z_FILTER_BREAK_FREQUENCY);
      filteredDesiredCoMHeightAlpha.set(AlphaFilteredYoVariable.computeAlphaGivenBreakFrequencyProperly(filteredDesiredCoMHeightAlphaBreakFrequency.getDoubleValue(), dt));
      filteredDesiredCoMHeightAlphaBreakFrequency.addVariableChangedListener(new VariableChangedListener()
      {

         @Override
         public void variableChanged(YoVariable<?> v)
         {
            filteredDesiredCoMHeightAlpha.set(AlphaFilteredYoVariable.computeAlphaGivenBreakFrequencyProperly(filteredDesiredCoMHeightAlphaBreakFrequency.getDoubleValue(), dt));

         }
      });
      
      desiredFeetAlphaFilterBreakFrequency.set(INITIAL_DESIRED_FOOT_CORRECTION_BREAK_FREQUENCY);
      desiredFeetAlphaFilterBreakFrequency.addVariableChangedListener(new VariableChangedListener()
      {

         @Override
         public void variableChanged(YoVariable<?> v)
         {
            double alpha = AlphaFilteredYoVariable.computeAlphaGivenBreakFrequencyProperly(desiredFeetAlphaFilterBreakFrequency.getDoubleValue(), dt);
            for (RobotQuadrant robotQuadrant : RobotQuadrant.values)
            {
               desiredFeetLocationsAlpha.get(robotQuadrant).set(alpha);
            }
         }
      });
      
      for (RobotQuadrant robotQuadrant : RobotQuadrant.values)
      {
         QuadrupedSwingTrajectoryGenerator swingTrajectoryGenerator = new QuadrupedSwingTrajectoryGenerator(referenceFrames, robotQuadrant, registry, yoGraphicsListRegistry, dt);
         swingTrajectoryGenerators.put(robotQuadrant, swingTrajectoryGenerator);
         
         ReferenceFrame footReferenceFrame = referenceFrames.getFootFrame(robotQuadrant);
         ReferenceFrame legAttachmentFrame = referenceFrames.getLegAttachmentFrame(robotQuadrant);
         
         legAttachmentFrames.put(robotQuadrant, legAttachmentFrame);

         String prefix = robotQuadrant.getCamelCaseNameForStartOfExpression();
         
         YoFramePoint actualFootPosition = new YoFramePoint(prefix + "actualFootPosition", ReferenceFrame.getWorldFrame(), registry);
         actualFeetLocations.put(robotQuadrant, actualFootPosition);
         
         DoubleYoVariable alpha = new DoubleYoVariable(prefix, registry);
         alpha.set(AlphaFilteredYoVariable.computeAlphaGivenBreakFrequencyProperly(desiredFeetAlphaFilterBreakFrequency.getDoubleValue(), dt));
         desiredFeetLocationsAlpha.put(robotQuadrant, alpha);
         
         AlphaFilteredYoFramePoint desiredFootLocation = AlphaFilteredYoFramePoint.createAlphaFilteredYoFramePoint(prefix + "FootDesiredPosition", "", registry, alpha, actualFootPosition);
         
         FramePoint footPosition = new FramePoint(footReferenceFrame);
         footPosition.changeFrame(ReferenceFrame.getWorldFrame());
         footPosition.setZ(0.0);
         desiredFootLocation.set(footPosition);
         desiredFeetLocations.put(robotQuadrant, desiredFootLocation);
      }
      
      createGraphicsAndArtifacts(yoGraphicsListRegistry);
      
      referenceFrames.updateFrames();
      updateFeetLocations();
      
      FramePose centerOfMassPose = new FramePose(comFrame);
      centerOfMassPose.changeFrame(ReferenceFrame.getWorldFrame());
      desiredCoMHeight.set(quadrupedControllerParameters.getInitalCoMHeight());
      filteredDesiredCoMHeight.update();
      centerOfMassPose.setZ(filteredDesiredCoMHeight.getDoubleValue());
      desiredCoMPose.set(centerOfMassPose);
      
      QuadrupleSupportState quadrupleSupportState = new QuadrupleSupportState(CrawlGateWalkingState.QUADRUPLE_SUPPORT);
      TripleSupportState tripleSupportState = new TripleSupportState(CrawlGateWalkingState.TRIPLE_SUPPORT);
      
      walkingStateMachine.addState(quadrupleSupportState);
      walkingStateMachine.addState(tripleSupportState);
      walkingStateMachine.setCurrentState(CrawlGateWalkingState.QUADRUPLE_SUPPORT);

      StateTransitionCondition quadrupleToTripleCondition = new StateTransitionCondition()
      {
         @Override
         public boolean checkCondition()
         {
            boolean isCommonTriangleNull = quadrupleSupportState.isCommonTriangleNull();
            
            return (isCommonTriangleNull || (!isCommonTriangleNull && quadrupleSupportState.isCoMInsideCommonTriangle())) && (desiredVelocity.length() != 0.0 || desiredYawRate.getDoubleValue() != 0); //bodyTrajectoryGenerator.isDone() &&
         }
      };

      StateTransitionCondition tripleToQuadrupleCondition = new StateTransitionCondition()
      {
         @Override
         public boolean checkCondition()
         {
            return isSwingFinished(swingLeg.getEnumValue());
         }
      };

      quadrupleSupportState.addStateTransition(new StateTransition<CrawlGateWalkingState>(CrawlGateWalkingState.TRIPLE_SUPPORT, quadrupleToTripleCondition));
      tripleSupportState.addStateTransition(new StateTransition<CrawlGateWalkingState>(CrawlGateWalkingState.QUADRUPLE_SUPPORT, tripleToQuadrupleCondition));
   }


   private VariableChangedListener createBreakFrequencyChangeListener(final double dt, final DoubleYoVariable breakFrequency, final DoubleYoVariable alpha)
   {
      return new VariableChangedListener()
      {
         @Override
         public void variableChanged(YoVariable<?> v)
         {
            double newAlpha = AlphaFilteredYoVariable.computeAlphaGivenBreakFrequencyProperly(breakFrequency.getDoubleValue(), dt);
            alpha.set(newAlpha);
         }
      };
   }


   private void createGraphicsAndArtifacts(YoGraphicsListRegistry yoGraphicsListRegistry)
   {
      YoArtifactPolygon supportPolygonArtifact = new YoArtifactPolygon("quadSupportPolygonArtifact", supportPolygon, Color.blue, false);
      YoArtifactPolygon currentTriplePolygonArtifact = new YoArtifactPolygon("currentTriplePolygonArtifact", currentTriplePolygon, Color.GREEN, false);
      YoArtifactPolygon upcommingTriplePolygonArtifact = new YoArtifactPolygon("upcommingTriplePolygonArtifact", upcommingTriplePolygon, Color.yellow, false);
      YoArtifactPolygon commonTriplePolygonArtifact = new YoArtifactPolygon("commonTriplePolygonArtifact", commonTriplePolygon, Color.RED, false);
      
      yoGraphicsListRegistry.registerArtifact("supportPolygon", supportPolygonArtifact);
      yoGraphicsListRegistry.registerArtifact("currentTriplePolygon", currentTriplePolygonArtifact);
      yoGraphicsListRegistry.registerArtifact("upcommingTriplePolygon", upcommingTriplePolygonArtifact);
      yoGraphicsListRegistry.registerArtifact("commonTriplePolygon", commonTriplePolygonArtifact);
      
      yoGraphicsListRegistry.registerArtifact("inscribedCircle", inscribedCircle);
      yoGraphicsListRegistry.registerArtifact("circleCenterViz", circleCenterGraphic.createArtifact());
      yoGraphicsListRegistry.registerArtifact("centerOfMassViz", centerOfMassViz.createArtifact());
      yoGraphicsListRegistry.registerArtifact("currentSwingTarget", currentSwingTargetViz.createArtifact());
      yoGraphicsListRegistry.registerArtifact("desiredCoMTarget", desiredCoMTargetViz.createArtifact());
      yoGraphicsListRegistry.registerArtifact("desiredCoMViz", desiredCoMViz.createArtifact());
      yoGraphicsListRegistry.registerArtifact("currentICPViz", currentICPViz.createArtifact());
      
      yoGraphicsListRegistry.registerArtifact("nominalYawArtifact", nominalYawArtifact);
      
      yoGraphicsListRegistry.registerYoGraphic("centerOfMassViz", centerOfMassViz);
      yoGraphicsListRegistry.registerYoGraphic("desiredCoMPoseYoGraphic", desiredCoMPoseYoGraphic);
      yoGraphicsListRegistry.registerYoGraphic("comPoseYoGraphic", comPoseYoGraphic);
      yoGraphicsListRegistry.registerYoGraphic("leftMidZUpFrameViz", leftMidZUpFrameViz);
      yoGraphicsListRegistry.registerYoGraphic("rightMidZUpFrameViz", rightMidZUpFrameViz);
      
      for (RobotQuadrant robotQuadrant : RobotQuadrant.values)
      {
         String prefix = robotQuadrant.getCamelCaseNameForStartOfExpression();
         
         YoFramePoint footPosition = actualFeetLocations.get(robotQuadrant);
         YoGraphicPosition actualFootPositionViz = new YoGraphicPosition(prefix + "actualFootPositionViz", footPosition, 0.02,
               getYoAppearance(robotQuadrant), GraphicType.BALL_WITH_CROSS);
         
         yoGraphicsListRegistry.registerYoGraphic("actualFootPosition", actualFootPositionViz);
         yoGraphicsListRegistry.registerArtifact("actualFootPosition", actualFootPositionViz.createArtifact());
         
         YoFramePoint desiredFootPosition = desiredFeetLocations.get(robotQuadrant);
         YoGraphicPosition desiredFootPositionViz = new YoGraphicPosition(prefix + "desiredFootPositionViz", desiredFootPosition, 0.01,
               YoAppearance.Red());
//         desiredFootPositionViz.hideGraphicObject();
         
         yoGraphicsListRegistry.registerYoGraphic("Desired Feet", desiredFootPositionViz);
         yoGraphicsListRegistry.registerArtifact("Desired Feet", desiredFootPositionViz.createArtifact());
      }
   }
   
   private AppearanceDefinition getYoAppearance(RobotQuadrant robotQuadrant)
   {
      switch (robotQuadrant)
      {
      case FRONT_LEFT:
         return YoAppearance.White();
      case FRONT_RIGHT:
         return YoAppearance.Yellow();
      case HIND_LEFT:
         return YoAppearance.Blue();
      case HIND_RIGHT:
         return YoAppearance.Black();
      default:
         throw new RuntimeException("bad quad");
      }
   }

   @Override
   public void doControl()
   {
      referenceFrames.updateFrames();
      updateEstimates();
      updateGraphics();
      updateFeetLocations();
      walkingStateMachine.checkTransitionConditions();
      walkingStateMachine.doAction();
      updateDesiredBodyIK();
      updateDesiredBody();
      updateLegsBasedOnDesiredBody();
   }
   
   private void updateEstimates()
   {
	  // compute center of mass position and velocity
	  FramePoint comPosition = new FramePoint(comFrame);
	  comPosition.changeFrame(ReferenceFrame.getWorldFrame());
	  FrameVector comVelocity = new FrameVector();
	  centerOfMassJacobian.compute();
	  centerOfMassJacobian.packCenterOfMassVelocity(comVelocity);
	  comVelocity.changeFrame(ReferenceFrame.getWorldFrame());
	  
	  // compute instantaneous capture point
	  double zFoot = actualFeetLocations.get(fourFootSupportPolygon.getLowestFootstep()).getZ();
	  double zDelta = comPosition.getZ() - zFoot;
	  double omega = Math.sqrt(9.81/zDelta);
	  currentICP.setX(comPosition.getX() + comVelocity.getX()/omega);
	  currentICP.setY(comPosition.getY() + comVelocity.getY()/omega);
	  currentICP.setZ(zFoot);
   }

   private void updateGraphics()
   {
      desiredCoMPoseYoGraphic.update();
      leftMidZUpFrameViz.update();
      rightMidZUpFrameViz.update();
      desiredCoMViz.update();
      comPoseYoGraphic.update();
      centerOfMassFramePoint.setToZero(comFrame);
      centerOfMassFramePoint.changeFrame(ReferenceFrame.getWorldFrame());
      centerOfMassPosition.set(centerOfMassFramePoint);
   }
   
   FramePoint footLocation = new FramePoint();

   private void updateFeetLocations()
   {
      for(RobotQuadrant robotQuadrant : RobotQuadrant.values)
      {
         YoFramePoint yoFootLocation = actualFeetLocations.get(robotQuadrant);
         yoFootLocation.getFrameTuple(footLocation);
         
         ReferenceFrame footFrame = referenceFrames.getFootFrame(robotQuadrant);
         footLocation.setToZero(footFrame);
         footLocation.changeFrame(ReferenceFrame.getWorldFrame());
         yoFootLocation.set(footLocation);
//         if(enableFootAlpha.getBooleanValue())
         {
            desiredFeetLocations.get(robotQuadrant).update();
         }
         
         fourFootSupportPolygon.setFootstep(robotQuadrant, footLocation);
      }
      drawSupportPolygon(fourFootSupportPolygon, supportPolygon);
   }
   
   private void updateDesiredBodyIK()
   {
      if(!bodyTrajectoryGenerator.isDone())
      {
         FramePoint desiredBodyFramePose = new FramePoint(ReferenceFrame.getWorldFrame());
         bodyMovementTrajectoryTimeCurrent.set(robotTimestamp.getDoubleValue() - bodyMovementTrajectoryTimeStart.getDoubleValue());
         bodyTrajectoryGenerator.compute(bodyMovementTrajectoryTimeCurrent.getDoubleValue());
         bodyTrajectoryGenerator.get(desiredBodyFramePose);
         desiredBodyFramePose.setZ(desiredCoMPose.getPosition().getZ());
         desiredCoMPose.setPosition(desiredBodyFramePose);
      }
   }

   private void updateDesiredBody()
   {
      if(robotTimestamp.getDoubleValue() > 1.0 && !enableFootAlpha.getBooleanValue())
      {
         enableFootAlpha.set(true);
         desiredFeetAlphaFilterBreakFrequency.set(DEFAULT_DESIRED_FOOT_CORRECTION_BREAK_FREQUENCY);
      }
      
      FramePoint centroidFramePoint = fourFootSupportPolygon.getCentroidFramePoint();
      nominalYaw.set(fourFootSupportPolygon.getNominalYaw());
      
      FramePoint2d centroidFramePoint2d = centroidFramePoint.toFramePoint2d();
      endPoint2d.set(centroidFramePoint2d);
      endPoint2d.add(0.4,0.0);
      endPoint2d.set(endPoint2d.yawAboutPoint(centroidFramePoint2d, nominalYaw.getDoubleValue()));
      
      nominalYawLineSegment.set(centroidFramePoint2d, endPoint2d);
      DoubleYoVariable desiredYaw = desiredCoMOrientation.getYaw();
      desiredYaw.set(nominalYaw.getDoubleValue());
      
      filteredDesiredCoMYaw.update();
      filteredDesiredCoMPitch.update();
      filteredDesiredCoMRoll.update();
      
      filteredDesiredCoMHeight.update();
      desiredCoMPosition.setZ(filteredDesiredCoMHeight.getDoubleValue());
      
      FramePose updatedPose = new FramePose(ReferenceFrame.getWorldFrame());
      desiredCoMPose.getFramePose(updatedPose);
      desiredCoM.set(updatedPose.getFramePointCopy());
      desiredCoMPoseReferenceFrame.setPoseAndUpdate(updatedPose);
   }
   
   private void updateLegsBasedOnDesiredBody()
   {
      for (RobotQuadrant robotQuadrant : RobotQuadrant.values)
      {
         Vector3d footPositionInLegAttachmentFrame = packFootPositionUsingDesiredBodyToBodyHack(robotQuadrant);
         computeDesiredPositionsAndStoreInFullRobotModel(robotQuadrant, footPositionInLegAttachmentFrame);
      }
   }
   
   private void computeDesiredPositionsAndStoreInFullRobotModel(RobotQuadrant robotQuadrant, Vector3d footPositionInLegAttachmentFrame)
   {
      inverseKinematicsCalculators.solveForEndEffectorLocationInBodyAndUpdateDesireds(robotQuadrant, footPositionInLegAttachmentFrame, fullRobotModel);
   }
   
   private Vector3d packFootPositionUsingDesiredBodyToBodyHack(RobotQuadrant robotQuadrant)
   {
      FramePoint desiredFootPosition = desiredFeetLocations.get(robotQuadrant).getFramePointCopy();
      desiredFootPosition.changeFrame(desiredCoMPoseReferenceFrame);

      FramePoint desiredFootPositionInBody = new FramePoint(comFrame, desiredFootPosition.getPoint());

      ReferenceFrame legAttachmentFrame = referenceFrames.getLegAttachmentFrame(robotQuadrant);
      desiredFootPositionInBody.changeFrame(legAttachmentFrame);

      Vector3d footPositionInLegAttachmentFrame = desiredFootPositionInBody.getVectorCopy();
      return footPositionInLegAttachmentFrame;
   }
   
   private void initializeSwingTrajectory(RobotQuadrant swingLeg, FramePoint swingInitial, FramePoint swingTarget, double swingTime)
   {
      QuadrupedSwingTrajectoryGenerator swingTrajectoryGenerator = swingTrajectoryGenerators.get(swingLeg);
      swingTrajectoryGenerator.initializeSwing(swingTime, swingInitial, swingTarget);
   }

   private void computeFootPositionAlongSwingTrajectory(RobotQuadrant swingLeg, FramePoint framePointToPack)
   {
      QuadrupedSwingTrajectoryGenerator swingTrajectoryGenerator = swingTrajectoryGenerators.get(swingLeg);
      swingTrajectoryGenerator.computeSwing(framePointToPack);
   }
   
   private boolean isSwingFinished(RobotQuadrant swingLeg)
   {
      QuadrupedSwingTrajectoryGenerator miniBeastSwingTrajectoryGenerator = swingTrajectoryGenerators.get(swingLeg);
      return miniBeastSwingTrajectoryGenerator.isDone();
   }
   
   private QuadrupedSupportPolygon copyCurrentSupportPolygonWithNewFootPosition(RobotQuadrant robotQuadrant, FramePoint footPosition)
   {
      QuadrupedSupportPolygon swingLegSupportPolygon = fourFootSupportPolygon.replaceFootstepCopy(robotQuadrant, footPosition);
      return swingLegSupportPolygon;
   }
   
   private QuadrupedSupportPolygon getCommonSupportPolygon(RobotQuadrant swingLeg, FramePoint desiredPosition)
   {
      QuadrupedSupportPolygon swingLegSupportPolygon = fourFootSupportPolygon.deleteLegCopy(swingLeg);
      drawSupportPolygon(swingLegSupportPolygon, currentTriplePolygon);
      
      RobotQuadrant nextRegularGaitSwingQuadrant = swingLeg.getNextRegularGaitSwingQuadrant();
      QuadrupedSupportPolygon nextSwingLegSupportPolygon = copyCurrentSupportPolygonWithNewFootPosition(swingLeg, desiredPosition);
      nextSwingLegSupportPolygon.deleteLeg(nextRegularGaitSwingQuadrant);
      drawSupportPolygon(nextSwingLegSupportPolygon, upcommingTriplePolygon);
      
      QuadrupedSupportPolygon shrunkenCommonSupportPolygon = swingLegSupportPolygon.getShrunkenCommonSupportPolygon(nextSwingLegSupportPolygon, swingLeg, 0.02, 0.02, 0.02);
      if(shrunkenCommonSupportPolygon != null)
      {
         drawSupportPolygon(shrunkenCommonSupportPolygon, commonTriplePolygon);
      }
      
      return shrunkenCommonSupportPolygon;
   }
   
   private void initializeBodyTrajectory(RobotQuadrant nextSwingLeg, QuadrupedSupportPolygon commonTriangle)
   {
      if(commonTriangle != null)
      {
         commonSupportPolygon.set(commonTriangle);
         
         FramePoint desiredBodyCurrent = desiredCoMPose.getPosition().getFramePointCopy();
//         FramePoint desiredBodyFinal = commonSupportPolygon.getCentroidFramePoint();
         
         boolean ttrCircleSuccess = false;
         double radius = subCircleRadius.getDoubleValue();
         if(useSubCircleForBodyShiftTarget.getBooleanValue())
         {
            ttrCircleSuccess = commonSupportPolygon.getTangentTangentRadiusCircleCenter(nextSwingLeg, radius, circleCenter2d);
         }
         
         if(!ttrCircleSuccess)
         {
            radius = commonSupportPolygon.getInCircle(circleCenter2d);
         }
         inscribedCircleRadius.set(radius);
         
         circleCenter.setXY(circleCenter2d);
         
         initialCoMPosition.set(desiredBodyCurrent);
         desiredCoMTarget.setXY(circleCenter2d);
         desiredCoMTarget.setZ(desiredBodyCurrent.getZ());
         
         bodyTrajectoryGenerator.initialize();
         bodyMovementTrajectoryTimeStart.set(robotTimestamp.getDoubleValue());
      }
   }

   public void calculateSwingTarget(RobotQuadrant swingLeg, FramePoint framePointToPack)
   {
      FrameVector desiredVelocityVector = desiredVelocity.getFrameTuple();
      double yawRate = desiredYawRate.getDoubleValue();
      
      switch (selectedSwingTargetGenerator.getEnumValue())
      {
         case INPLACE:
         {
            YoFramePoint yoFootPosition = actualFeetLocations.get(swingLeg);
            FramePoint footPosition = yoFootPosition.getFrameTuple();
            footPosition.changeFrame(ReferenceFrame.getWorldFrame());
            framePointToPack.set(footPosition);
            break;
         }
         case MIDZUP:
         {
            zUpSwingTargetGenerator.getSwingTarget(swingLeg, desiredVelocityVector, framePointToPack, yawRate);
            break;
         }
      }
   }
   
   private void drawSupportPolygon(QuadrupedSupportPolygon supportPolygon, YoFrameConvexPolygon2d yoFramePolygon)
   {
      ConvexPolygon2d polygon = new ConvexPolygon2d();
      for(RobotQuadrant quadrant : RobotQuadrant.values)
      {
         FramePoint footstep = supportPolygon.getFootstep(quadrant);
         if(footstep != null)
         {
            polygon.addVertex(footstep.getX(), footstep.getY());
         }
      }
      polygon.update();
      yoFramePolygon.setConvexPolygon2d(polygon);
   }
   
   private class QuadrupleSupportState extends State<CrawlGateWalkingState>
   {
      private final FramePoint nextEstimatedFootPosition = new FramePoint();
      private QuadrupedSupportPolygon nextEstimatedCommonTriangle;
      
      public QuadrupleSupportState(CrawlGateWalkingState stateEnum)
      {
         super(stateEnum);
      }

      @Override
      public void doAction()
      {
         
      }

      @Override
      public void doTransitionIntoAction()
      {
         RobotQuadrant currentSwingLeg = swingLeg.getEnumValue();
         RobotQuadrant nextRegularGaitSwingQuadrant = currentSwingLeg.getNextRegularGaitSwingQuadrant();
         swingLeg.set(nextRegularGaitSwingQuadrant);
         
         RobotQuadrant robotQuadrant = swingLeg.getEnumValue();
         
         nextEstimatedFootPosition.changeFrame(ReferenceFrame.getWorldFrame());
         calculateSwingTarget(robotQuadrant, nextEstimatedFootPosition);
         nextEstimatedCommonTriangle = getCommonSupportPolygon(robotQuadrant, nextEstimatedFootPosition);
         initializeBodyTrajectory(robotQuadrant, nextEstimatedCommonTriangle);
      }

      @Override
      public void doTransitionOutOfAction()
      {
        
      }
      
      public boolean isCommonTriangleNull()
      {
         return nextEstimatedCommonTriangle == null;
      }
      
      public boolean isCoMInsideCommonTriangle()
      {
         centerOfMassFramePoint.changeFrame(ReferenceFrame.getWorldFrame());
         centerOfMassFramePoint.getPoint2d(centerOfMassPoint2d);
        
         return nextEstimatedCommonTriangle.isInside(centerOfMassPoint2d);
      }
   }

   private class TripleSupportState extends State<CrawlGateWalkingState>
   {
      private final FramePoint swingTarget = new FramePoint(ReferenceFrame.getWorldFrame());
      private final FramePoint currentDesiredInTrajectory = new FramePoint();
      private final Vector3d footPositionInLegAttachmentFrame = new Vector3d();
      public TripleSupportState(CrawlGateWalkingState stateEnum)
      {
         super(stateEnum);
      }

      @Override
      public void doAction()
      {
         RobotQuadrant swingQuadrant = swingLeg.getEnumValue();
         
         computeFootPositionAlongSwingTrajectory(swingQuadrant, currentDesiredInTrajectory);
         currentDesiredInTrajectory.changeFrame(ReferenceFrame.getWorldFrame());
         currentSwingTarget.set(currentDesiredInTrajectory);
         currentDesiredInTrajectory.changeFrame(bodyFrame);
         
         desiredFeetLocations.get(swingQuadrant).setAndMatchFrame(currentDesiredInTrajectory);

         currentDesiredInTrajectory.changeFrame(referenceFrames.getLegAttachmentFrame(swingQuadrant));
         currentDesiredInTrajectory.get(footPositionInLegAttachmentFrame);
         computeDesiredPositionsAndStoreInFullRobotModel(swingQuadrant, footPositionInLegAttachmentFrame);
      }

      @Override
      public void doTransitionIntoAction()
      {        
         RobotQuadrant swingQuadrant = swingLeg.getEnumValue();
         YoFramePoint yoDesiredFootPosition = desiredFeetLocations.get(swingQuadrant);
         YoFramePoint yoActualFootPosition = actualFeetLocations.get(swingQuadrant);
         swingTarget.changeFrame(ReferenceFrame.getWorldFrame());
         calculateSwingTarget(swingQuadrant, swingTarget);
         currentSwingTarget.set(swingTarget);
         
         initializeSwingTrajectory(swingQuadrant, yoDesiredFootPosition.getFramePointCopy(), swingTarget, swingDuration.getDoubleValue());
      }

      @Override
      public void doTransitionOutOfAction()
      {
      }
   }

   @Override
   public void initialize()
   {
      
   }


   @Override
   public YoVariableRegistry getYoVariableRegistry()
   {
      return registry;
   }


   @Override
   public String getName()
   {
      return null;
   }


   @Override
   public String getDescription()
   {
      return null;
   }
}