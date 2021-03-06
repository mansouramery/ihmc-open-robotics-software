package us.ihmc.stateEstimation.humanoid.kinematicsBasedStateEstimation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.vecmath.Vector3d;

import us.ihmc.commonWalkingControlModules.sensors.footSwitch.FootSwitchInterface;
import us.ihmc.graphics3DAdapter.graphics.appearances.YoAppearance;
import us.ihmc.humanoidRobotics.bipedSupportPolygons.ContactablePlaneBody;
import us.ihmc.humanoidRobotics.model.CenterOfPressureDataHolder;
import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.dataStructures.variable.BooleanYoVariable;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.geometry.FrameConvexPolygon2d;
import us.ihmc.robotics.geometry.FrameLineSegment2d;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.geometry.FramePoint2d;
import us.ihmc.robotics.geometry.FrameVector;
import us.ihmc.robotics.geometry.RigidBodyTransform;
import us.ihmc.robotics.math.filters.AlphaFilteredYoFramePoint2d;
import us.ihmc.robotics.math.filters.AlphaFilteredYoFrameVector;
import us.ihmc.robotics.math.filters.BacklashCompensatingVelocityYoFrameVector;
import us.ihmc.robotics.math.frames.YoFramePoint;
import us.ihmc.robotics.math.frames.YoFramePoint2d;
import us.ihmc.robotics.math.frames.YoFrameVector;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.screwTheory.RigidBody;
import us.ihmc.robotics.screwTheory.SixDoFJoint;
import us.ihmc.robotics.screwTheory.Twist;
import us.ihmc.robotics.screwTheory.TwistCalculator;
import us.ihmc.sensorProcessing.stateEstimation.evaluation.FullInverseDynamicsStructure;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicPosition;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicsListRegistry;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.plotting.YoArtifactPosition;


/**
 * PelvisKinematicsBasedPositionCalculator estimates the pelvis position and linear velocity using the leg kinematics.
 * @author Sylvain
 *
 */
public class PelvisKinematicsBasedLinearStateCalculator
{
   private static final boolean VISUALIZE = true;

   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());

   private final TwistCalculator twistCalculator;

   private final SixDoFJoint rootJoint;
   private final List<RigidBody> feetRigidBodies;

   private final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();
   private final ReferenceFrame rootJointFrame;
   private final Map<RigidBody, ReferenceFrame> soleFrames = new LinkedHashMap<RigidBody, ReferenceFrame>();
   private final Map<RigidBody, ReferenceFrame> copFrames = new LinkedHashMap<RigidBody, ReferenceFrame>();

   private final YoFramePoint rootJointPosition = new YoFramePoint("estimatedRootJointPositionWithKinematics", worldFrame, registry);
   private final YoFrameVector rootJointLinearVelocity = new YoFrameVector("estimatedRootJointVelocityWithKinematics", worldFrame, registry);
   private final BooleanYoVariable useTwistToComputeRootJointLinearVelocity = new BooleanYoVariable("useTwistToComputeRootJointLinearVelocity", registry);

   private final Map<RigidBody, YoFrameVector> footVelocitiesInWorld = new LinkedHashMap<RigidBody, YoFrameVector>();
   private final Map<RigidBody, Twist> footTwistsInWorld = new LinkedHashMap<RigidBody, Twist>();
   private final YoFrameVector rootJointLinearVelocityNewTwist = new YoFrameVector("estimatedRootJointVelocityNewTwist", worldFrame, registry);
   private final DoubleYoVariable alphaRootJointLinearVelocityNewTwist = new DoubleYoVariable("alphaRootJointLinearVelocityNewTwist", registry);

   private final DoubleYoVariable alphaRootJointLinearVelocityBacklashKinematics = new DoubleYoVariable("alphaRootJointLinearVelocityBacklashKinematics", registry);
   private final DoubleYoVariable slopTimeRootJointLinearVelocityBacklashKinematics = new DoubleYoVariable("slopTimeRootJointLinearVelocityBacklashKinematics", registry);
   private final BacklashCompensatingVelocityYoFrameVector rootJointLinearVelocityBacklashKinematics;

   private final DoubleYoVariable alphaFootToRootJointPosition = new DoubleYoVariable("alphaFootToRootJointPosition", registry);
   private final Map<RigidBody, AlphaFilteredYoFrameVector> footToRootJointPositions = new LinkedHashMap<RigidBody, AlphaFilteredYoFrameVector>();
   private final Map<RigidBody, YoFramePoint> footPositionsInWorld = new LinkedHashMap<RigidBody, YoFramePoint>();

   private final Map<RigidBody, YoFramePoint> copPositionsInWorld = new LinkedHashMap<RigidBody, YoFramePoint>();

   private final DoubleYoVariable alphaCoPFilter = new DoubleYoVariable("alphaCoPFilter", registry);
   private final Map<RigidBody, AlphaFilteredYoFramePoint2d> copsFilteredInFootFrame = new LinkedHashMap<RigidBody, AlphaFilteredYoFramePoint2d>();
   private final Map<RigidBody, YoFramePoint2d> copsRawInFootFrame = new LinkedHashMap<RigidBody, YoFramePoint2d>();

   private final Map<RigidBody, FrameConvexPolygon2d> footPolygons = new LinkedHashMap<RigidBody, FrameConvexPolygon2d>();
   private final Map<RigidBody, FrameLineSegment2d> footCenterCoPLineSegments = new LinkedHashMap<RigidBody, FrameLineSegment2d>();

   private final BooleanYoVariable kinematicsIsUpToDate = new BooleanYoVariable("kinematicsIsUpToDate", registry);
   private final BooleanYoVariable useControllerDesiredCoP = new BooleanYoVariable("useControllerDesiredCoP", registry);
   private final BooleanYoVariable trustCoPAsNonSlippingContactPoint = new BooleanYoVariable("trustCoPAsNonSlippingContactPoint", registry);

   // temporary variables
   private final FramePoint tempFramePoint = new FramePoint();
   private final FrameVector tempFrameVector = new FrameVector();
   private final FramePoint tempPosition = new FramePoint();
   private final FramePoint2d tempCoP2d = new FramePoint2d();
   private final FramePoint tempCoP = new FramePoint();
   private final FrameVector tempCoPOffset = new FrameVector();

   private final Map<RigidBody, FootSwitchInterface> footSwitches;
   private final CenterOfPressureDataHolder centerOfPressureDataHolderFromController;

   public PelvisKinematicsBasedLinearStateCalculator(FullInverseDynamicsStructure inverseDynamicsStructure, Map<RigidBody, ? extends ContactablePlaneBody> feetContactablePlaneBodies,
         Map<RigidBody, FootSwitchInterface> footSwitches, CenterOfPressureDataHolder centerOfPressureDataHolderFromController, double estimatorDT,
         YoGraphicsListRegistry yoGraphicsListRegistry, YoVariableRegistry parentRegistry)
   {
      this.rootJoint = inverseDynamicsStructure.getRootJoint();
      this.rootJointFrame = rootJoint.getFrameAfterJoint();
      this.twistCalculator = inverseDynamicsStructure.getTwistCalculator();
      this.footSwitches = footSwitches;
      this.centerOfPressureDataHolderFromController = centerOfPressureDataHolderFromController;
      this.feetRigidBodies = new ArrayList<>(feetContactablePlaneBodies.keySet());

      rootJointLinearVelocityBacklashKinematics = BacklashCompensatingVelocityYoFrameVector.createBacklashCompensatingVelocityYoFrameVector("estimatedRootJointLinearVelocityBacklashKin", "",
            alphaRootJointLinearVelocityBacklashKinematics, estimatorDT, slopTimeRootJointLinearVelocityBacklashKinematics, registry, rootJointPosition);

      for (int i = 0; i < feetRigidBodies.size(); i++)
      {
         RigidBody foot = feetRigidBodies.get(i);
         ReferenceFrame soleFrame = feetContactablePlaneBodies.get(foot).getSoleFrame();
         soleFrames.put(foot, soleFrame);

         String namePrefix = foot.getName();

         AlphaFilteredYoFrameVector footToRootJointPosition = AlphaFilteredYoFrameVector.createAlphaFilteredYoFrameVector(namePrefix + "FootToRootJointPosition", "", registry, alphaFootToRootJointPosition, worldFrame);
         footToRootJointPositions.put(foot, footToRootJointPosition);

         YoFramePoint footPositionInWorld = new YoFramePoint(namePrefix + "FootPositionInWorld", worldFrame, registry);
         footPositionsInWorld.put(foot, footPositionInWorld);

         FrameConvexPolygon2d footPolygon = new FrameConvexPolygon2d(feetContactablePlaneBodies.get(foot).getContactPoints2d());
         footPolygons.put(foot, footPolygon);

         FrameLineSegment2d tempLineSegment = new FrameLineSegment2d(new FramePoint2d(soleFrame), new FramePoint2d(soleFrame, 1.0, 1.0)); // TODO need to give distinct points that's not convenient
         footCenterCoPLineSegments.put(foot, tempLineSegment);

         YoFramePoint2d copRawInFootFrame = new YoFramePoint2d(namePrefix + "CoPRawInFootFrame", soleFrames.get(foot), registry);
         copsRawInFootFrame.put(foot, copRawInFootFrame);

         final AlphaFilteredYoFramePoint2d copFilteredInFootFrame = AlphaFilteredYoFramePoint2d.createAlphaFilteredYoFramePoint2d(namePrefix + "CoPFilteredInFootFrame", "", registry, alphaCoPFilter, copRawInFootFrame);
         copFilteredInFootFrame.update(0.0, 0.0); // So the next point will be filtered
         copsFilteredInFootFrame.put(foot, copFilteredInFootFrame);

         YoFramePoint copPositionInWorld = new YoFramePoint(namePrefix + "CoPPositionsInWorld", worldFrame, registry);
         copPositionsInWorld.put(foot, copPositionInWorld);

         YoFrameVector footVelocityInWorld = new YoFrameVector(namePrefix + "VelocityInWorld", worldFrame, registry);
         footVelocitiesInWorld.put(foot, footVelocityInWorld);

         footTwistsInWorld.put(foot, new Twist());

         ReferenceFrame copFrame = new ReferenceFrame("copFrame", soleFrame)
         {
            private static final long serialVersionUID = -1926704435608610401L;
            private final Vector3d copOffset = new Vector3d();
            @Override
            protected void updateTransformToParent(RigidBodyTransform transformToParent)
            {
               transformToParent.setIdentity();
               copFilteredInFootFrame.get(copOffset);
               transformToParent.setTranslation(copOffset);
            }
         };
         copFrames.put(foot, copFrame);
      }

      if (VISUALIZE)
      {
         if (yoGraphicsListRegistry != null)
         {
            for (int i = 0; i < feetRigidBodies.size(); i++)
            {
               RigidBody foot = feetRigidBodies.get(i);
               String sidePrefix = foot.getName();
               YoGraphicPosition copInWorld = new YoGraphicPosition(sidePrefix + "StateEstimatorCoP", copPositionsInWorld.get(foot), 0.005, YoAppearance.DeepPink());
               YoArtifactPosition artifact = copInWorld.createArtifact();
               artifact.setVisible(false);
               yoGraphicsListRegistry.registerArtifact("StateEstimator", artifact);
            }
         }
      }

      parentRegistry.addChild(registry);
   }

   public void setTrustCoPAsNonSlippingContactPoint(boolean trustCoP)
   {
      trustCoPAsNonSlippingContactPoint.set(trustCoP);
   }

   public void useControllerDesiredCoP(boolean useControllerDesiredCoP)
   {
      this.useControllerDesiredCoP.set(useControllerDesiredCoP);
   }

   public void setAlphaPelvisPosition(double alphaFilter)
   {
      alphaFootToRootJointPosition.set(alphaFilter);
   }

   public void setPelvisLinearVelocityAlphaNewTwist(double alpha)
   {
      alphaRootJointLinearVelocityNewTwist.set(alpha);
   }

   public void setPelvisLinearVelocityBacklashParameters(double alphaFilter, double slopTime)
   {
      alphaRootJointLinearVelocityBacklashKinematics.set(alphaFilter);
      slopTimeRootJointLinearVelocityBacklashKinematics.set(slopTime);
   }

   public void setAlphaCenterOfPressure(double alphaFilter)
   {
      alphaCoPFilter.set(alphaFilter);
   }

   public void enableTwistEstimation(boolean enable)
   {
      useTwistToComputeRootJointLinearVelocity.set(enable);
   }

   private void reset()
   {
      rootJointPosition.setToZero();
      rootJointLinearVelocity.setToZero();
   }

   /**
    * Estimates the foot positions corresponding to the given pelvisPosition
    * @param pelvisPosition
    */
   public void initialize(FramePoint pelvisPosition)
   {
      updateKinematics();
      setPelvisPosition(pelvisPosition);

      for (int i = 0; i < feetRigidBodies.size(); i++)
      {
         RigidBody foot = feetRigidBodies.get(i);
         updateFootPosition(foot, pelvisPosition);
      }
      kinematicsIsUpToDate.set(false);
   }

   /**
    * Estimates the pelvis position and linear velocity using the leg kinematics
    * @param trustedFoot is the foot used to estimates the pelvis state
    * @param numberOfTrustedSides is only one or both legs used to estimate the pelvis state
    */
   private void updatePelvisWithKinematics(RigidBody trustedFoot, int numberOfTrustedFeet)
   {
      double scaleFactor = 1.0 / numberOfTrustedFeet;

      footToRootJointPositions.get(trustedFoot).getFrameTuple(tempPosition);
      tempPosition.scale(scaleFactor);
      rootJointPosition.add(tempPosition);
      footPositionsInWorld.get(trustedFoot).getFrameTuple(tempPosition);
      tempPosition.scale(scaleFactor);
      rootJointPosition.add(tempPosition);

      footVelocitiesInWorld.get(trustedFoot).getFrameTupleIncludingFrame(tempFrameVector);
      tempFrameVector.scale(scaleFactor * alphaRootJointLinearVelocityNewTwist.getDoubleValue());
      rootJointLinearVelocityNewTwist.sub(tempFrameVector);
   }

   /**
    * updates the position of a swinging foot
    * @param swingingFoot a foot in swing
    * @param pelvisPosition the current pelvis position
    */
   private void updateFootPosition(RigidBody swingingFoot, FramePoint pelvisPosition)
   {
      YoFramePoint footPositionInWorld = footPositionsInWorld.get(swingingFoot);
      footPositionInWorld.set(footToRootJointPositions.get(swingingFoot));
      footPositionInWorld.scale(-1.0);
      footPositionInWorld.add(pelvisPosition);

      copPositionsInWorld.get(swingingFoot).set(footPositionInWorld);

      copsRawInFootFrame.get(swingingFoot).setToZero();
      copsFilteredInFootFrame.get(swingingFoot).setToZero();
   }

   /**
    * Compute the foot CoP. The CoP is the point on the support foot trusted to be not slipping.
    * @param trustedSide
    * @param footSwitch
    */
   private void updateCoPPosition(RigidBody trustedFoot)
   {
      AlphaFilteredYoFramePoint2d copFilteredInFootFrame = copsFilteredInFootFrame.get(trustedFoot);
      ReferenceFrame footFrame = soleFrames.get(trustedFoot);

      if (useControllerDesiredCoP.getBooleanValue())
         centerOfPressureDataHolderFromController.getCenterOfPressure(tempCoP2d, trustedFoot);
      else
         footSwitches.get(trustedFoot).computeAndPackCoP(tempCoP2d);

      if (trustCoPAsNonSlippingContactPoint.getBooleanValue())
      {
         if (tempCoP2d.containsNaN())
         {
            tempCoP2d.setToZero();
         }
         else
         {
            FrameConvexPolygon2d footPolygon = footPolygons.get(trustedFoot);
            boolean isCoPInsideFoot = footPolygon.isPointInside(tempCoP2d);
            if (!isCoPInsideFoot)
            {
               if (footSwitches.get(trustedFoot).computeFootLoadPercentage() > 0.2)
               {
                  FrameLineSegment2d footCenterCoPLineSegment = footCenterCoPLineSegments.get(trustedFoot);
                  footCenterCoPLineSegment.set(footFrame, 0.0, 0.0, tempCoP2d.getX(), tempCoP2d.getY());
                  // TODO Garbage
                  FramePoint2d[] intersectionPoints = footPolygon.intersectionWith(footCenterCoPLineSegment);

                  if (intersectionPoints.length == 2)
                     System.out.println("In " + getClass().getSimpleName() + ": Found two solutions for the CoP projection.");

                  if (intersectionPoints.length == 0)
                  {
                     System.out.println("In " + getClass().getSimpleName() + ": Found no solution for the CoP projection.");
                     tempCoP2d.setToZero(footFrame);
                  }
                  else
                     tempCoP2d.set(intersectionPoints[0]);
               }
               else // If foot barely loaded and actual CoP outside, then don't update the raw CoP right below
               {
                  copsRawInFootFrame.get(trustedFoot).getFrameTuple2dIncludingFrame(tempCoP2d);
               }
            }
         }
      }
      else
      {
         tempCoP2d.setToZero();
      }

      copsRawInFootFrame.get(trustedFoot).set(tempCoP2d);

      tempCoPOffset.setIncludingFrame(footFrame, copFilteredInFootFrame.getX(), copFilteredInFootFrame.getY(), 0.0);
      copFilteredInFootFrame.update();
      tempCoPOffset.setIncludingFrame(footFrame, copFilteredInFootFrame.getX() - tempCoPOffset.getX(), copFilteredInFootFrame.getY() - tempCoPOffset.getY(), 0.0);

      tempCoPOffset.changeFrame(worldFrame);
      copPositionsInWorld.get(trustedFoot).add(tempCoPOffset);
   }

   /**
    * Assuming the CoP is not moving, the foot position can be updated. That way we can see if the foot is on the edge.
    * @param plantedFoot
    */
   private void correctFootPositionsUsingCoP(RigidBody plantedFoot)
   {
      AlphaFilteredYoFramePoint2d copFilteredInFootFrame = copsFilteredInFootFrame.get(plantedFoot);
      tempCoPOffset.setIncludingFrame(copFilteredInFootFrame.getReferenceFrame(), copFilteredInFootFrame.getX(), copFilteredInFootFrame.getY(), 0.0);

      tempCoPOffset.changeFrame(worldFrame);

      YoFramePoint footPositionIWorld = footPositionsInWorld.get(plantedFoot);
      footPositionIWorld.set(copPositionsInWorld.get(plantedFoot));
      footPositionIWorld.sub(tempCoPOffset);
   }

   /**
    * Updates the different kinematics related stuff that is used to estimate the pelvis state
    */
   public void updateKinematics()
   {
      reset();
      updateKinematicsNewTwist();

      twistCalculator.compute();

      for (int i = 0; i < feetRigidBodies.size(); i++)
      {
         RigidBody foot = feetRigidBodies.get(i);
         tempFramePoint.setToZero(rootJointFrame);
         tempFramePoint.changeFrame(soleFrames.get(foot));

         tempFrameVector.setIncludingFrame(tempFramePoint);
         tempFrameVector.changeFrame(worldFrame);

         footToRootJointPositions.get(foot).update(tempFrameVector);
      }

      kinematicsIsUpToDate.set(true);
   }

   private final Twist tempRootBodyTwist = new Twist();

   private void updateKinematicsNewTwist()
   {
      rootJoint.getJointTwist(tempRootBodyTwist);

      rootJointLinearVelocityNewTwist.getFrameTupleIncludingFrame(tempFrameVector);
      tempFrameVector.changeFrame(tempRootBodyTwist.getExpressedInFrame());

      tempRootBodyTwist.setLinearPart(tempFrameVector);
      rootJoint.setJointTwist(tempRootBodyTwist);

      twistCalculator.compute();

      for (int i = 0; i < feetRigidBodies.size(); i++)
      {
         RigidBody foot = feetRigidBodies.get(i);
         Twist footTwistInWorld = footTwistsInWorld.get(foot);
         YoFrameVector footVelocityInWorld = footVelocitiesInWorld.get(foot);

         twistCalculator.getTwistOfBody(footTwistInWorld, foot);
         footTwistInWorld.changeBodyFrameNoRelativeTwist(soleFrames.get(foot));
         footTwistInWorld.changeFrame(soleFrames.get(foot));

         this.copsFilteredInFootFrame.get(foot).getFrameTuple2dIncludingFrame(tempCoP2d);
         tempCoP.setXYIncludingFrame(tempCoP2d);
         footTwistInWorld.changeFrame(footTwistInWorld.getBaseFrame());
         tempCoP.changeFrame(footTwistInWorld.getExpressedInFrame());
         footTwistInWorld.getLinearVelocityOfPointFixedInBodyFrame(tempFrameVector, tempCoP);

         tempFrameVector.changeFrame(worldFrame);
         footVelocityInWorld.set(tempFrameVector);
      }
   }

   public void updateFeetPositionsWhenTrustingIMUOnly(FramePoint pelvisPosition)
   {
      for (int i = 0; i < feetRigidBodies.size(); i++)
      {
         RigidBody foot = feetRigidBodies.get(i);
         updateFootPosition(foot, pelvisPosition);
      }
   }

   public void estimatePelvisLinearState(List<RigidBody> trustedFeet, List<RigidBody> unTrustedFeet, FramePoint pelvisPosition)
   {
      if (!kinematicsIsUpToDate.getBooleanValue())
         throw new RuntimeException("Leg kinematics needs to be updated before trying to estimate the pelvis position/linear velocity.");

      for(int i = 0; i < trustedFeet.size(); i++)
      {
         RigidBody trustedFoot = trustedFeet.get(i);
         updateCoPPosition(trustedFoot);
         correctFootPositionsUsingCoP(trustedFoot);
         updatePelvisWithKinematics(trustedFoot, trustedFeet.size());
      }

      rootJointLinearVelocityBacklashKinematics.update();

      kinematicsIsUpToDate.set(false);

      for(int i = 0; i < unTrustedFeet.size(); i++)
      {
         RigidBody unTrustedFoot = unTrustedFeet.get(i);
         updateFootPosition(unTrustedFoot, pelvisPosition);
      }

      kinematicsIsUpToDate.set(false);
   }

   public void setPelvisPosition(FramePoint pelvisPosition)
   {
      rootJointPosition.set(pelvisPosition);
   }

   public void getRootJointPositionAndVelocity(FramePoint positionToPack, FrameVector linearVelocityToPack)
   {
      getPelvisPosition(positionToPack);
      getPelvisVelocity(linearVelocityToPack);
   }

   public void getPelvisPosition(FramePoint positionToPack)
   {
      rootJointPosition.getFrameTupleIncludingFrame(positionToPack);
   }

   public void getPelvisVelocity(FrameVector linearVelocityToPack)
   {
      rootJointLinearVelocityNewTwist.getFrameTupleIncludingFrame(linearVelocityToPack);
   }

   public void getFootToPelvisPosition(FramePoint positionToPack, RigidBody foot)
   {
      footToRootJointPositions.get(foot).getFrameTupleIncludingFrame(positionToPack);
   }
}
