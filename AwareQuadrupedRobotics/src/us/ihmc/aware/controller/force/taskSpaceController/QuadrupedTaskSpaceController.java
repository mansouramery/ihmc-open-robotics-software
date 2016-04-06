package us.ihmc.aware.controller.force.taskSpaceController;

import us.ihmc.SdfLoader.SDFFullRobotModel;
import us.ihmc.aware.controller.force.taskSpaceController.feedbackController.QuadrupedBodyOrientationFeedbackController;
import us.ihmc.aware.controller.force.taskSpaceController.feedbackController.QuadrupedComPositionFeedbackController;
import us.ihmc.aware.controller.force.taskSpaceController.feedbackController.QuadrupedSolePositionFeebackController;
import us.ihmc.aware.controller.force.taskSpaceController.feedbackController.QuadrupedTaskSpaceFeedbackController;
import us.ihmc.aware.util.ContactState;
import us.ihmc.aware.vmc.*;
import us.ihmc.quadrupedRobotics.parameters.QuadrupedJointNameMap;
import us.ihmc.quadrupedRobotics.referenceFrames.QuadrupedReferenceFrames;
import us.ihmc.quadrupedRobotics.virtualModelController.QuadrupedJointLimits;
import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.geometry.FrameVector;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.robotSide.QuadrantDependentList;
import us.ihmc.robotics.robotSide.RobotQuadrant;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicsListRegistry;

import java.util.ArrayList;

public class QuadrupedTaskSpaceController
{
   private final QuadrupedJointLimits jointLimits;
   private final QuadrupedVirtualModelController virtualModelController;
   private final QuadrupedVirtualModelControllerSettings virtualModelControllerSettings;
   private final QuadrupedContactForceLimits contactForceLimits;
   private final QuadrupedContactForceOptimization contactForceOptimization;
   private final QuadrupedContactForceOptimizationSettings contactForceOptimizationSettings;
   private final FrameVector contactForceStorage;
   private final QuadrupedComPositionFeedbackController comPositionFeedbackController;
   private final QuadrupedBodyOrientationFeedbackController bodyOrientationFeedbackController;
   private final QuadrantDependentList<QuadrupedSolePositionFeebackController> solePositionFeedbackController;
   private final ArrayList<QuadrupedTaskSpaceFeedbackController> feedbackControllers;
   private final QuadrupedTaskSpaceCommands feedbackCommands;

   private final YoVariableRegistry registry = new YoVariableRegistry("taskSpaceController");

   public QuadrupedTaskSpaceController(SDFFullRobotModel fullRobotModel, QuadrupedReferenceFrames referenceFrames, QuadrupedJointNameMap jointNameMap, QuadrupedJointLimits jointLimits, double controlDT, YoVariableRegistry parentRegistry)
   {
      this.jointLimits = jointLimits;

      // virtual model controller
      virtualModelController = new QuadrupedVirtualModelController(fullRobotModel, referenceFrames, jointNameMap, registry);
      virtualModelControllerSettings = new QuadrupedVirtualModelControllerSettings();
      contactForceLimits = new QuadrupedContactForceLimits();
      contactForceOptimization = new QuadrupedContactForceOptimization(referenceFrames, registry);
      contactForceOptimizationSettings = new QuadrupedContactForceOptimizationSettings();
      contactForceStorage = new FrameVector();

      // feedback controllers
      comPositionFeedbackController = new QuadrupedComPositionFeedbackController(referenceFrames.getCenterOfMassZUpFrame(), controlDT, registry);
      bodyOrientationFeedbackController = new QuadrupedBodyOrientationFeedbackController(referenceFrames.getBodyFrame(), controlDT, registry);
      solePositionFeedbackController = new QuadrantDependentList<>();
      for (RobotQuadrant robotQuadrant : RobotQuadrant.values)
      {
         solePositionFeedbackController.set(robotQuadrant, new QuadrupedSolePositionFeebackController(robotQuadrant, referenceFrames.getFootFrame(robotQuadrant), controlDT, registry));
      }
      feedbackControllers = new ArrayList<>();
      feedbackControllers.add(comPositionFeedbackController);
      feedbackControllers.add(bodyOrientationFeedbackController);
      for (RobotQuadrant robotQuadrant : RobotQuadrant.values)
      {
         feedbackControllers.add(solePositionFeedbackController.get(robotQuadrant));
      }
      feedbackCommands = new QuadrupedTaskSpaceCommands();
      parentRegistry.addChild(registry);
      reset();
   }

   public void reset()
   {
      virtualModelController.reset();
      contactForceOptimization.reset();
      for (int i = 0; i < feedbackControllers.size(); i++)
      {
         feedbackControllers.get(i).reset();
      }
   }

   public void registerGraphics(YoGraphicsListRegistry yoGraphicsListRegistry)
   {
      virtualModelController.registerGraphics(yoGraphicsListRegistry);
      for (int i = 0; i < feedbackControllers.size(); i++)
      {
         feedbackControllers.get(i).registerGraphics(yoGraphicsListRegistry);
      }
   }

   public void compute(QuadrupedTaskSpaceControllerSettings settings, QuadrupedTaskSpaceSetpoints setpoints, QuadrupedTaskSpaceEstimates estimates, QuadrupedTaskSpaceCommands commands)
   {
      // initialize commands
      commands.setToZero();
      commands.changeFrame(ReferenceFrame.getWorldFrame());

      // initialize settings
      for (RobotQuadrant robotQuadrant : RobotQuadrant.values)
      {
         solePositionFeedbackController.get(robotQuadrant).getGains().reset();
         if (settings.getContactState(robotQuadrant) == ContactState.NO_CONTACT)
         {
            settings.getSolePositionFeedbackGains(robotQuadrant, solePositionFeedbackController.get(robotQuadrant).getGains());
         }
      }
      settings.getBodyOrientationFeedbackGains(bodyOrientationFeedbackController.getGains());
      settings.getComPositionFeedbackGains(comPositionFeedbackController.getGains());
      settings.getContactForceOptimizationSettings(contactForceOptimizationSettings);
      settings.getContactForceLimits(contactForceLimits);
      settings.getVirtualModelControllerSettings(virtualModelControllerSettings);

      // compute commands
      for (int i = 0; i < feedbackControllers.size(); i++)
      {
         feedbackCommands.setToZero();
         feedbackControllers.get(i).computeFeedback(estimates, setpoints, feedbackCommands);
         feedbackCommands.changeFrame(ReferenceFrame.getWorldFrame());
         commands.add(feedbackCommands);
      }

      // compute optimal contact force distribution for quadrants that are in contact
      for (RobotQuadrant robotQuadrant : RobotQuadrant.values)
      {
         // note: sole forces are inverted to obtain commanded reaction forces
         commands.getSoleForce().get(robotQuadrant).scale(-1.0);
         contactForceOptimization.setContactForceCommand(robotQuadrant, commands.getSoleForce().get(robotQuadrant));
         commands.getSoleForce().get(robotQuadrant).scale(-1.0);
         contactForceOptimization.setContactState(robotQuadrant, settings.getContactState(robotQuadrant));
      }
      contactForceOptimization.setComForceCommand(commands.getComForce());
      contactForceOptimization.setComTorqueCommand(commands.getComTorque());
      contactForceOptimization.solve(contactForceLimits, contactForceOptimizationSettings);

      // compute leg joint torques using jacobian transpose
      for (RobotQuadrant robotQuadrant : RobotQuadrant.values)
      {
         if (settings.getContactState(robotQuadrant) == ContactState.IN_CONTACT)
         {
            contactForceOptimization.getContactForceSolution(robotQuadrant, contactForceStorage);
            virtualModelController.setSoleContactForce(robotQuadrant, contactForceStorage);
            virtualModelController.setSoleContactForceVisible(robotQuadrant, true);
            virtualModelController.setSoleVirtualForceVisible(robotQuadrant, false);
         }
         else
         {
            commands.getSoleForce(robotQuadrant).changeFrame(ReferenceFrame.getWorldFrame());
            if (commands.getSoleForce(robotQuadrant).getZ() < -contactForceLimits.getPressureUpperLimit(robotQuadrant))
            {
               // apply friction pyramid limits to sole forces if contact conditions are detected during NO_CONTACT state
               double fx = commands.getSoleForce(robotQuadrant).getX();
               double fy = commands.getSoleForce(robotQuadrant).getY();
               double mu = contactForceLimits.getCoefficientOfFriction(robotQuadrant);
               commands.getSoleForce(robotQuadrant).setX(Math.min(fx, mu * contactForceLimits.getPressureUpperLimit(robotQuadrant) / Math.sqrt(2)));
               commands.getSoleForce(robotQuadrant).setX(Math.max(fx,-mu * contactForceLimits.getPressureUpperLimit(robotQuadrant) / Math.sqrt(2)));
               commands.getSoleForce(robotQuadrant).setY(Math.min(fy, mu * contactForceLimits.getPressureUpperLimit(robotQuadrant) / Math.sqrt(2)));
               commands.getSoleForce(robotQuadrant).setY(Math.max(fy,-mu * contactForceLimits.getPressureUpperLimit(robotQuadrant) / Math.sqrt(2)));
               commands.getSoleForce(robotQuadrant).setZ(-contactForceLimits.getPressureUpperLimit(robotQuadrant));
            }
            virtualModelController.setSoleVirtualForce(robotQuadrant, commands.getSoleForce(robotQuadrant));
            virtualModelController.setSoleContactForceVisible(robotQuadrant, false);
            virtualModelController.setSoleVirtualForceVisible(robotQuadrant, true);
         }
      }
      virtualModelController.compute(jointLimits, virtualModelControllerSettings);
   }

   public ContactState getContactState(RobotQuadrant robotQuadrant)
   {
      return contactForceOptimization.getContactState(robotQuadrant);
   }
}
