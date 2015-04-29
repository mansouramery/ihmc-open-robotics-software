package us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.manipulation.individual.states;

import java.util.LinkedHashMap;
import java.util.Map;

import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.manipulation.individual.HandControlState;
import us.ihmc.commonWalkingControlModules.momentumBasedController.MomentumBasedController;
import us.ihmc.utilities.FormattingTools;
import us.ihmc.utilities.math.MathTools;
import us.ihmc.utilities.screwTheory.InverseDynamicsJoint;
import us.ihmc.utilities.screwTheory.OneDoFJoint;
import us.ihmc.utilities.screwTheory.RevoluteJoint;
import us.ihmc.utilities.screwTheory.ScrewTools;
import us.ihmc.yoUtilities.controllers.PIDController;
import us.ihmc.yoUtilities.controllers.YoPIDGains;
import us.ihmc.yoUtilities.dataStructure.registry.YoVariableRegistry;
import us.ihmc.yoUtilities.dataStructure.variable.BooleanYoVariable;
import us.ihmc.yoUtilities.dataStructure.variable.DoubleYoVariable;
import us.ihmc.yoUtilities.math.filters.RateLimitedYoVariable;
import us.ihmc.yoUtilities.math.trajectories.DoubleTrajectoryGenerator;
import us.ihmc.yoUtilities.math.trajectories.OneDoFJointTrajectoryGenerator;
import us.ihmc.yoUtilities.stateMachines.State;

public class JointSpaceHandControlState extends State<HandControlState>
{
   private final OneDoFJoint[] oneDoFJoints;
   private Map<OneDoFJoint, ? extends OneDoFJointTrajectoryGenerator> trajectories;
   private final LinkedHashMap<OneDoFJoint, PIDController> pidControllers;

   private final LinkedHashMap<OneDoFJoint, RateLimitedYoVariable> rateLimitedAccelerations;

   private final DoubleYoVariable maxAcceleration;

   private final YoVariableRegistry registry;
   private final MomentumBasedController momentumBasedController;
   private final BooleanYoVariable initialized;

   private final boolean doPositionControl;

   private final double dt;
   private final boolean[] doIntegrateDesiredAccelerations;

   private final boolean[] hasJointBeenDisabledAtLeastOnce;

   public JointSpaceHandControlState(String namePrefix, InverseDynamicsJoint[] controlledJoints, boolean doPositionControl,
         MomentumBasedController momentumBasedController, YoPIDGains gains, double dt, YoVariableRegistry parentRegistry)
   {
      super(HandControlState.JOINT_SPACE);

      this.dt = dt;
      this.doPositionControl = doPositionControl;

      String name = namePrefix + getClass().getSimpleName();
      registry = new YoVariableRegistry(name);

      this.oneDoFJoints = ScrewTools.filterJoints(controlledJoints, RevoluteJoint.class);
      initialized = new BooleanYoVariable(name + "Initialized", registry);
      initialized.set(false);
      hasJointBeenDisabledAtLeastOnce = new boolean[oneDoFJoints.length];

      if (!doPositionControl)
      {
         maxAcceleration = gains.getYoMaximumAcceleration();
         pidControllers = new LinkedHashMap<OneDoFJoint, PIDController>();
         rateLimitedAccelerations = new LinkedHashMap<OneDoFJoint, RateLimitedYoVariable>();

         for (OneDoFJoint joint : oneDoFJoints)
         {
            String suffix = FormattingTools.lowerCaseFirstLetter(joint.getName());
            PIDController pidController = new PIDController(gains.getYoKp(), gains.getYoKi(), gains.getYoKd(), gains.getYoMaxIntegralError(), suffix, registry);
            pidControllers.put(joint, pidController);

            RateLimitedYoVariable rateLimitedAcceleration = new RateLimitedYoVariable(suffix + "Acceleration", registry, gains.getYoMaximumJerk(), dt);
            rateLimitedAccelerations.put(joint, rateLimitedAcceleration);
         }
      }
      else
      {
         maxAcceleration = null;
         pidControllers = null;
         rateLimitedAccelerations = null;
      }

      this.momentumBasedController = momentumBasedController;

      doIntegrateDesiredAccelerations = new boolean[oneDoFJoints.length];

      for (int i = 0; i < oneDoFJoints.length; i++)
      {
         OneDoFJoint joint = oneDoFJoints[i];
         doIntegrateDesiredAccelerations[i] = joint.getIntegrateDesiredAccelerations();
      }

      parentRegistry.addChild(registry);
   }

   @Override
   public void doAction()
   {
      for (int i = 0; i < oneDoFJoints.length; i++)
      {
         OneDoFJoint joint = oneDoFJoints[i];

         DoubleTrajectoryGenerator trajectoryGenerator = trajectories.get(joint);
         trajectoryGenerator.compute(getTimeInCurrentState());

         double currentPosition = joint.getQ();
         double currentVelocity = joint.getQd();

         if (!joint.isEnabled())
            hasJointBeenDisabledAtLeastOnce[i] = true;

         // The joint uncontrollable so just all the desired position to the actual, and the rest to zero.
         if (hasJointBeenDisabledAtLeastOnce[i])
         {
            joint.setqDesired(currentPosition);
            joint.setQdDesired(0.0);
            if (!doPositionControl)
            {
               RateLimitedYoVariable rateLimitedAcceleration = rateLimitedAccelerations.get(joint);
               rateLimitedAcceleration.set(0.0);
            }
            momentumBasedController.setOneDoFJointAcceleration(joint, 0.0);
         }
         else if (doPositionControl)
         {
            joint.setqDesired(trajectoryGenerator.getValue());
            joint.setQdDesired(trajectoryGenerator.getVelocity());
            double desiredAcceleration = trajectoryGenerator.getAcceleration();
            momentumBasedController.setOneDoFJointAcceleration(joint, desiredAcceleration);
         }
         else
         {
            joint.setqDesired(trajectoryGenerator.getValue());
            joint.setQdDesired(trajectoryGenerator.getVelocity());
            double feedforwardAcceleration = trajectoryGenerator.getAcceleration();

            PIDController pidController = pidControllers.get(joint);
            double desiredAcceleration = feedforwardAcceleration
                  + pidController.computeForAngles(currentPosition, joint.getqDesired(), currentVelocity, joint.getQdDesired(), dt);

            desiredAcceleration = MathTools.clipToMinMax(desiredAcceleration, maxAcceleration.getDoubleValue());

            RateLimitedYoVariable rateLimitedAcceleration = rateLimitedAccelerations.get(joint);
            rateLimitedAcceleration.update(desiredAcceleration);
            desiredAcceleration = rateLimitedAcceleration.getDoubleValue();

            momentumBasedController.setOneDoFJointAcceleration(joint, desiredAcceleration);
         }
      }
   }

   @Override
   public void doTransitionIntoAction()
   {
      if (initialized.getBooleanValue() && getPreviousState() == this)
      {
         for (int i = 0; i < oneDoFJoints.length; i++)
         {
            OneDoFJoint joint = oneDoFJoints[i];
            trajectories.get(joint).initialize( joint.getqDesired(), joint.getQdDesired());
         }
      }
      else
      {
         for (int i = 0; i < oneDoFJoints.length; i++)
         {
            OneDoFJoint joint = oneDoFJoints[i];
            trajectories.get(joint).initialize();

            if (!doPositionControl)
               pidControllers.get(joint).setCumulativeError(0.0);
         }
         initialized.set(true);
      }

      saveDoAccelerationIntegration();

      if (doPositionControl)
         enablePositionControl();

      for (int i = 0; i < oneDoFJoints.length; i++)
         hasJointBeenDisabledAtLeastOnce[i] = !oneDoFJoints[i].isEnabled();
   }

   @Override
   public void doTransitionOutOfAction()
   {
      disablePositionControl();
   }

   private void saveDoAccelerationIntegration()
   {
      for (int i = 0; i < oneDoFJoints.length; i++)
      {
         OneDoFJoint joint = oneDoFJoints[i];
         doIntegrateDesiredAccelerations[i] = joint.getIntegrateDesiredAccelerations();
      }
   }

   private void enablePositionControl()
   {
      for (int i = 0; i < oneDoFJoints.length; i++)
      {
         OneDoFJoint joint = oneDoFJoints[i];
         joint.setIntegrateDesiredAccelerations(false);
         joint.setUnderPositionControl(true);
      }
   }

   private void disablePositionControl()
   {
      for (int i = 0; i < oneDoFJoints.length; i++)
      {
         OneDoFJoint joint = oneDoFJoints[i];
         joint.setIntegrateDesiredAccelerations(doIntegrateDesiredAccelerations[i]);
         joint.setUnderPositionControl(false);
      }
   }

   public boolean isDone()
   {
      for (OneDoFJoint oneDoFJoint : oneDoFJoints)
      {
         if (!trajectories.get(oneDoFJoint).isDone())
            return false;
      }

      return true;
   }

   public void setTrajectories(Map<OneDoFJoint, ? extends OneDoFJointTrajectoryGenerator> trajectories)
   {
      this.trajectories = trajectories;
   }
}