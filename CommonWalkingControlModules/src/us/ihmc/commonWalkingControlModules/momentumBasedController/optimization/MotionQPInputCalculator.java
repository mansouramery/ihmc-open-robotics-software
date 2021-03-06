package us.ihmc.commonWalkingControlModules.momentumBasedController.optimization;

import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.CommonOps;

import gnu.trove.list.array.TIntArrayList;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseDynamics.JointspaceAccelerationCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseDynamics.MomentumRateCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseDynamics.PointAccelerationCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseDynamics.SpatialAccelerationCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseKinematics.JointLimitReductionCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseKinematics.JointspaceVelocityCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseKinematics.MomentumCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseKinematics.PrivilegedConfigurationCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseKinematics.SpatialVelocityCommand;
import us.ihmc.commonWalkingControlModules.inverseKinematics.JointPrivilegedConfigurationHandler;
import us.ihmc.commonWalkingControlModules.momentumBasedController.GeometricJacobianHolder;
import us.ihmc.robotics.MathTools;
import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.geometry.FrameVector;
import us.ihmc.robotics.linearAlgebra.MatrixTools;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.screwTheory.ConvectiveTermCalculator;
import us.ihmc.robotics.screwTheory.GeometricJacobian;
import us.ihmc.robotics.screwTheory.InverseDynamicsJoint;
import us.ihmc.robotics.screwTheory.OneDoFJoint;
import us.ihmc.robotics.screwTheory.PointJacobian;
import us.ihmc.robotics.screwTheory.PointJacobianConvectiveTermCalculator;
import us.ihmc.robotics.screwTheory.RigidBody;
import us.ihmc.robotics.screwTheory.ScrewTools;
import us.ihmc.robotics.screwTheory.SpatialAccelerationVector;
import us.ihmc.robotics.screwTheory.SpatialForceVector;
import us.ihmc.robotics.screwTheory.SpatialMotionVector;
import us.ihmc.robotics.screwTheory.Twist;
import us.ihmc.robotics.screwTheory.TwistCalculator;

public class MotionQPInputCalculator
{
   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());
   private final DoubleYoVariable nullspaceProjectionAlpha = new DoubleYoVariable("nullspaceProjectionAlpha", registry);
   private final DoubleYoVariable secondaryTaskJointsWeight = new DoubleYoVariable("secondaryTaskJointsWeight", registry);

   private final GeometricJacobianHolder geometricJacobianHolder;

   private final PointJacobian pointJacobian = new PointJacobian();
   private final PointJacobianConvectiveTermCalculator pointJacobianConvectiveTermCalculator;

   private final InverseDynamicsJoint[] jointsToOptimizeFor;
   private final OneDoFJoint[] oneDoFJoints;

   private final DenseMatrix64F jointsRangeOfMotion;
   private final DenseMatrix64F jointLowerLimits;
   private final DenseMatrix64F jointUpperLimits;

   private final FramePoint tempBodyFixedPoint = new FramePoint();
   private final FrameVector pPointVelocity = new FrameVector();
   private final DenseMatrix64F tempPPointMatrixVelocity = new DenseMatrix64F(3, 1);
   private final DenseMatrix64F convectiveTermMatrix = new DenseMatrix64F(SpatialMotionVector.SIZE, 1);

   private final SpatialAccelerationVector convectiveTerm = new SpatialAccelerationVector();
   private final ConvectiveTermCalculator convectiveTermCalculator = new ConvectiveTermCalculator();

   private final CentroidalMomentumHandler centroidalMomentumHandler;

   private final JointPrivilegedConfigurationHandler privilegedConfigurationHandler;

   private final DenseMatrix64F tempTaskJacobian = new DenseMatrix64F(SpatialMotionVector.SIZE, 12);
   private final DenseMatrix64F tempCompactJacobian = new DenseMatrix64F(SpatialMotionVector.SIZE, 12);
   private final DenseMatrix64F tempTaskObjective = new DenseMatrix64F(SpatialMotionVector.SIZE, 1);
   private final DenseMatrix64F tempTaskAlphaTaskPriority = new DenseMatrix64F(SpatialAccelerationVector.SIZE, 1);
   private final DenseMatrix64F tempTaskWeight = new DenseMatrix64F(SpatialAccelerationVector.SIZE, SpatialAccelerationVector.SIZE);
   private final DenseMatrix64F tempTaskWeightSubspace = new DenseMatrix64F(SpatialAccelerationVector.SIZE, SpatialAccelerationVector.SIZE);

   private final double controlDT;

   private final JointIndexHandler jointIndexHandler;

   private final DenseMatrix64F allTaskJacobian;
   private final DampedLeastSquaresNullspaceCalculator nullspaceCalculator;

   private final int numberOfDoFs;

   public MotionQPInputCalculator(ReferenceFrame centerOfMassFrame, GeometricJacobianHolder geometricJacobianHolder, TwistCalculator twistCalculator,
         JointIndexHandler jointIndexHandler, double controlDT, YoVariableRegistry parentRegistry)
   {
      this.geometricJacobianHolder = geometricJacobianHolder;
      this.jointIndexHandler = jointIndexHandler;
      this.jointsToOptimizeFor = jointIndexHandler.getIndexedJoints();
      this.controlDT = controlDT;
      oneDoFJoints = jointIndexHandler.getIndexedOneDoFJoints();
      pointJacobianConvectiveTermCalculator = new PointJacobianConvectiveTermCalculator(twistCalculator);
      centroidalMomentumHandler = new CentroidalMomentumHandler(twistCalculator.getRootBody(), centerOfMassFrame, registry);
      privilegedConfigurationHandler = new JointPrivilegedConfigurationHandler(oneDoFJoints, registry);

      numberOfDoFs = jointIndexHandler.getNumberOfDoFs();
      allTaskJacobian = new DenseMatrix64F(numberOfDoFs, numberOfDoFs);
      secondaryTaskJointsWeight.set(1.0); // TODO Needs to be rethought, it doesn't seem to be that useful.
      nullspaceProjectionAlpha.set(0.005);
      nullspaceCalculator = new DampedLeastSquaresNullspaceCalculator(numberOfDoFs, nullspaceProjectionAlpha.getDoubleValue());

      jointsRangeOfMotion = new DenseMatrix64F(numberOfDoFs, 1);
      jointLowerLimits = new DenseMatrix64F(numberOfDoFs, 1);
      jointUpperLimits = new DenseMatrix64F(numberOfDoFs, 1);

      for (int i = 0; i < oneDoFJoints.length; i++)
      {
         OneDoFJoint joint = oneDoFJoints[i];
         int jointIndex = jointIndexHandler.getOneDoFJointIndex(joint);
         double jointLimitLower = joint.getJointLimitLower();
         double jointLimitUpper = joint.getJointLimitUpper();

         jointsRangeOfMotion.set(jointIndex, 0, jointLimitUpper - jointLimitLower);
         jointLowerLimits.set(jointIndex, 0, jointLimitLower);
         jointUpperLimits.set(jointIndex, 0, jointLimitUpper);
      }

      parentRegistry.addChild(registry);
   }

   public void initialize()
   {
      centroidalMomentumHandler.compute();
      allTaskJacobian.reshape(0, numberOfDoFs);
      privilegedConfigurationHandler.reset();
   }

   public void updatePrivilegedConfiguration(PrivilegedConfigurationCommand command)
   {
      privilegedConfigurationHandler.submitPrivilegedConfigurationCommand(command);
   }

   public void submitJointLimitReductionCommand(JointLimitReductionCommand command)
   {
      for (int commandJointIndex = 0; commandJointIndex < command.getNumberOfJoints(); commandJointIndex++)
      {
         OneDoFJoint joint = command.getJoint(commandJointIndex);
         int jointIndex = jointIndexHandler.getOneDoFJointIndex(joint);
         double originalJointLimitLower = joint.getJointLimitLower();
         double originalJointLimitUpper = joint.getJointLimitUpper();
         double limitReduction = command.getJointLimitReductionFactor(commandJointIndex) * jointsRangeOfMotion.get(jointIndex, 0);
         jointLowerLimits.set(jointIndex, 0, originalJointLimitLower + limitReduction);
         jointUpperLimits.set(jointIndex, 0, originalJointLimitUpper - limitReduction);
      }
   }

   public boolean computePrivilegedJointAccelerations(MotionQPInput motionQPInputToPack)
   {
      if (!privilegedConfigurationHandler.isEnabled())
         return false;

      privilegedConfigurationHandler.computePrivilegedJointAccelerations();

      motionQPInputToPack.setIsMotionConstraint(false);
      motionQPInputToPack.setUseWeightScalar(true);
      motionQPInputToPack.setWeight(privilegedConfigurationHandler.getWeight());

      nullspaceCalculator.setPseudoInverseAlpha(nullspaceProjectionAlpha.getDoubleValue());

      int taskSize = 0;

      for (int chainIndex = 0; chainIndex < privilegedConfigurationHandler.getNumberOfChains(); chainIndex++)
      {
         RigidBody chainBase = privilegedConfigurationHandler.getChainBase(chainIndex);
         RigidBody chainEndEffector = privilegedConfigurationHandler.getChainEndEffector(chainIndex);

         long chainJacobianId = geometricJacobianHolder.getOrCreateGeometricJacobian(chainBase, chainEndEffector, chainEndEffector.getBodyFixedFrame());
         GeometricJacobian chainJacobian = geometricJacobianHolder.getJacobian(chainJacobianId);
         InverseDynamicsJoint[] chainJoints = chainJacobian.getJointsInOrder();

         if (chainJoints.length == 0)
            continue;

         // Check that all the joints are indexed before doing the calculation.
         if (!jointIndexHandler.areJointsIndexed(chainJoints))
            continue;

         int chainNumberOfDoFs = chainJacobian.getNumberOfColumns();
         tempCompactJacobian.reshape(chainNumberOfDoFs, chainNumberOfDoFs);
         CommonOps.setIdentity(tempCompactJacobian);
         nullspaceCalculator.projectOntoNullspace(tempCompactJacobian, chainJacobian.getJacobianMatrix());

         tempTaskJacobian.reshape(chainNumberOfDoFs, numberOfDoFs);

         // Since we know here that all the joints are indexed this method call will succeed.
         jointIndexHandler.compactBlockToFullBlock(chainJoints, tempCompactJacobian, tempTaskJacobian);
//         recordTaskJacobian(tempTaskJacobian);

         motionQPInputToPack.reshape(taskSize + chainNumberOfDoFs);
         CommonOps.insert(tempTaskJacobian, motionQPInputToPack.taskJacobian, taskSize, 0);

         for (int i = 0; i < chainNumberOfDoFs; i++)
         {
            try
            {
               OneDoFJoint chainJoint = (OneDoFJoint) chainJoints[i];
               motionQPInputToPack.taskObjective.set(taskSize + i, 0, privilegedConfigurationHandler.getPrivilegedJointAcceleration(chainJoint));
            }
            catch (ClassCastException e)
            {
               throw new ClassCastException("Can only handle " + OneDoFJoint.class.getSimpleName() + ". Received unexpected joint: " + chainJoints[i].getName()
                     + ", joint class: " + chainJoints[i].getClass().getSimpleName());
            }
         }

         taskSize += chainNumberOfDoFs;
      }

      DenseMatrix64F selectionMatrix = privilegedConfigurationHandler.getSelectionMatrix();
      int robotTaskSize = selectionMatrix.getNumRows();

      if (robotTaskSize > 0)
      {
         OneDoFJoint[] joints = privilegedConfigurationHandler.getJoints();
         tempTaskJacobian.reshape(robotTaskSize, numberOfDoFs);
         boolean success = jointIndexHandler.compactBlockToFullBlock(joints, selectionMatrix, tempTaskJacobian);

         if (success)
         {
            motionQPInputToPack.reshape(taskSize + robotTaskSize);
            nullspaceCalculator.projectOntoNullspace(tempTaskJacobian, allTaskJacobian);
            CommonOps.insert(tempTaskJacobian, motionQPInputToPack.taskJacobian, taskSize, 0);
            CommonOps.insert(privilegedConfigurationHandler.getPrivilegedJointAccelerations(), motionQPInputToPack.taskObjective, taskSize, 0);
         }
      }

      return taskSize > 0;
   }

   public boolean computePrivilegedJointVelocities(MotionQPInput motionQPInputToPack)
   {
      if (!privilegedConfigurationHandler.isEnabled())
         return false;

      privilegedConfigurationHandler.computePrivilegedJointVelocities();

      motionQPInputToPack.setIsMotionConstraint(false);
      motionQPInputToPack.setUseWeightScalar(true);
      motionQPInputToPack.setWeight(privilegedConfigurationHandler.getWeight());

      DenseMatrix64F selectionMatrix = privilegedConfigurationHandler.getSelectionMatrix();

      int taskSize = selectionMatrix.getNumRows();

      if (taskSize == 0)
         return false;

      motionQPInputToPack.reshape(taskSize);

      motionQPInputToPack.setTaskObjective(privilegedConfigurationHandler.getPrivilegedJointVelocities());

      OneDoFJoint[] joints = privilegedConfigurationHandler.getJoints();
      boolean success = jointIndexHandler.compactBlockToFullBlock(joints, selectionMatrix, motionQPInputToPack.taskJacobian);

      if (!success)
         return false;

      nullspaceCalculator.projectOntoNullspace(motionQPInputToPack.taskJacobian, allTaskJacobian);

      return true;
   }

   /**
    * Converts a {@link PointAccelerationCommand} into a {@link MotionQPInput}.
    * @return true if the command was successfully converted.
    */
   public boolean convertPointAccelerationCommand(PointAccelerationCommand commandToConvert, MotionQPInput motionQPInputToPack)
   {
      DenseMatrix64F selectionMatrix = commandToConvert.getSelectionMatrix();
      int taskSize = selectionMatrix.getNumRows();

      if (taskSize == 0)
         return false;

      motionQPInputToPack.reshape(taskSize);
      motionQPInputToPack.setIsMotionConstraint(!commandToConvert.getHasWeight());
      if (commandToConvert.getHasWeight())
      {
         // Compute the weight: W = S * W * S^T
         motionQPInputToPack.setUseWeightScalar(false);
         tempTaskWeight.reshape(3, 3);
         commandToConvert.getWeightMatrix(tempTaskWeight);
         tempTaskWeightSubspace.reshape(taskSize, 3);
         CommonOps.mult(selectionMatrix, tempTaskWeight, tempTaskWeightSubspace);
         CommonOps.multTransB(tempTaskWeightSubspace, selectionMatrix, motionQPInputToPack.taskWeightMatrix);
      }

      RigidBody base = commandToConvert.getBase();
      RigidBody endEffector = commandToConvert.getEndEffector();
      long jacobianId = geometricJacobianHolder.getOrCreateGeometricJacobian(base, endEffector, base.getBodyFixedFrame());
      GeometricJacobian jacobian = geometricJacobianHolder.getJacobian(jacobianId);

      commandToConvert.getBodyFixedPointIncludingFrame(tempBodyFixedPoint);
      FrameVector desiredAccelerationWithRespectToBase = commandToConvert.getDesiredAcceleration();

      pointJacobian.set(jacobian, tempBodyFixedPoint);
      pointJacobian.compute();

      desiredAccelerationWithRespectToBase.changeFrame(jacobian.getBaseFrame());

      DenseMatrix64F pointJacobianMatrix = pointJacobian.getJacobianMatrix();

      tempTaskJacobian.reshape(selectionMatrix.getNumRows(), pointJacobianMatrix.getNumCols());
      CommonOps.mult(selectionMatrix, pointJacobianMatrix, tempTaskJacobian);
      boolean success = jointIndexHandler.compactBlockToFullBlock(jacobian.getJointsInOrder(), tempTaskJacobian, motionQPInputToPack.taskJacobian);

      if (!success)
         return false;

      recordTaskJacobian(motionQPInputToPack.taskJacobian);

      pointJacobianConvectiveTermCalculator.compute(pointJacobian, pPointVelocity);
      pPointVelocity.scale(-1.0);
      pPointVelocity.add(desiredAccelerationWithRespectToBase);
      MatrixTools.setDenseMatrixFromTuple3d(tempPPointMatrixVelocity, pPointVelocity.getVector(), 0, 0);
      CommonOps.mult(selectionMatrix, tempPPointMatrixVelocity, motionQPInputToPack.taskObjective);

      return true;
   }

   private final TIntArrayList tempJointIndices = new TIntArrayList();

   /**
    * Converts a {@link SpatialAccelerationCommand} into a {@link MotionQPInput}.
    * @return true if the command was successfully converted.
    */
   public boolean convertSpatialAccelerationCommand(SpatialAccelerationCommand commandToConvert, MotionQPInput motionQPInputToPack)
   {
      DenseMatrix64F selectionMatrix = commandToConvert.getSelectionMatrix();
      int taskSize = selectionMatrix.getNumRows();

      if (taskSize == 0)
         return false;

      if (commandToConvert.getAlphaTaskPriority() < 1.0e-5)
         return false;

      motionQPInputToPack.reshape(taskSize);
      motionQPInputToPack.setIsMotionConstraint(!commandToConvert.getHasWeight());
      if (commandToConvert.getHasWeight())
      {
         // Compute the weight: W = S * W * S^T
         motionQPInputToPack.setUseWeightScalar(false);
         tempTaskWeight.reshape(SpatialAccelerationVector.SIZE, SpatialAccelerationVector.SIZE);
         commandToConvert.getWeightMatrix(tempTaskWeight);
         tempTaskWeightSubspace.reshape(taskSize, SpatialAccelerationVector.SIZE);
         CommonOps.mult(selectionMatrix, tempTaskWeight, tempTaskWeightSubspace);
         CommonOps.multTransB(tempTaskWeightSubspace, selectionMatrix, motionQPInputToPack.taskWeightMatrix);
      }

      SpatialAccelerationVector spatialAcceleration = commandToConvert.getSpatialAcceleration();
      RigidBody base = commandToConvert.getBase();
      RigidBody endEffector = commandToConvert.getEndEffector();
      long jacobianId = geometricJacobianHolder.getOrCreateGeometricJacobian(base, endEffector, spatialAcceleration.getExpressedInFrame());
      GeometricJacobian jacobian = geometricJacobianHolder.getJacobian(jacobianId);

      // Compute the task Jacobian: J = S * J
      tempTaskJacobian.reshape(taskSize, jacobian.getNumberOfColumns());
      CommonOps.mult(selectionMatrix, jacobian.getJacobianMatrix(), tempTaskJacobian);

      RigidBody primaryBase = commandToConvert.getPrimaryBase();
      InverseDynamicsJoint[] jointsUsedInTask = jacobian.getJointsInOrder();
      if (primaryBase != null)
      {
         boolean isJointUpstreamOfPrimaryBase = false;

         for (int i = jointsUsedInTask.length - 1; i >= 0; i--)
         {
            InverseDynamicsJoint joint = jointsUsedInTask[i];

            if (joint.getSuccessor() == primaryBase)
               isJointUpstreamOfPrimaryBase = true;

            if (isJointUpstreamOfPrimaryBase)
            {
               tempJointIndices.reset();
               ScrewTools.computeIndexForJoint(jointsUsedInTask, tempJointIndices, joint);
               for (int j = 0; j < tempJointIndices.size(); j++)
                  MatrixTools.scaleColumn(secondaryTaskJointsWeight.getDoubleValue(), tempJointIndices.get(j), tempTaskJacobian);
            }
         }
      }

      jointIndexHandler.compactBlockToFullBlockIgnoreUnindexedJoints(jointsUsedInTask, tempTaskJacobian, motionQPInputToPack.taskJacobian);

      // Compute the task objective: p = S * ( TDot - JDot qDot )
      convectiveTermCalculator.computeJacobianDerivativeTerm(jacobian, convectiveTerm);
      convectiveTerm.getMatrix(convectiveTermMatrix, 0);
      spatialAcceleration.getMatrix(tempTaskObjective, 0);
      CommonOps.subtractEquals(tempTaskObjective, convectiveTermMatrix);
      CommonOps.mult(selectionMatrix, tempTaskObjective, motionQPInputToPack.taskObjective);

      if (commandToConvert.getAlphaTaskPriority() < 1.0 - 1.0e-5)
      {
         CommonOps.scale(commandToConvert.getAlphaTaskPriority(), motionQPInputToPack.taskJacobian);
         CommonOps.scale(commandToConvert.getAlphaTaskPriority(), motionQPInputToPack.taskObjective);
      }

      recordTaskJacobian(motionQPInputToPack.taskJacobian);

      return true;
   }

   /**
    * Converts a {@link SpatialVelocityCommand} into a {@link MotionQPInput}.
    * @return true if the command was successfully converted.
    */
   public boolean convertSpatialVelocityCommand(SpatialVelocityCommand commandToConvert, MotionQPInput motionQPInputToPack)
   {
      DenseMatrix64F selectionMatrix = commandToConvert.getSelectionMatrix();
      int taskSize = selectionMatrix.getNumRows();

      if (taskSize == 0)
         return false;

      motionQPInputToPack.reshape(taskSize);
      motionQPInputToPack.setIsMotionConstraint(commandToConvert.isHardConstraint());
      if (!commandToConvert.isHardConstraint())
      {
         motionQPInputToPack.setUseWeightScalar(true);
         motionQPInputToPack.setWeight(commandToConvert.getWeight());
      }

      Twist spatialVelocity = commandToConvert.getSpatialVelocity();
      RigidBody base = commandToConvert.getBase();
      RigidBody endEffector = commandToConvert.getEndEffector();
      long jacobianId = geometricJacobianHolder.getOrCreateGeometricJacobian(base, endEffector, spatialVelocity.getExpressedInFrame());
      GeometricJacobian jacobian = geometricJacobianHolder.getJacobian(jacobianId);

      // Compute the task Jacobian: J = S * J
      tempTaskJacobian.reshape(taskSize, jacobian.getNumberOfColumns());
      CommonOps.mult(selectionMatrix, jacobian.getJacobianMatrix(), tempTaskJacobian);
      boolean success = jointIndexHandler.compactBlockToFullBlock(jacobian.getJointsInOrder(), tempTaskJacobian, motionQPInputToPack.taskJacobian);

      if (!success)
         return false;

      recordTaskJacobian(motionQPInputToPack.taskJacobian);

      // Compute the task objective: p = S * T
      spatialVelocity.getMatrix(tempTaskObjective, 0);
      CommonOps.mult(selectionMatrix, tempTaskObjective, motionQPInputToPack.taskObjective);

      return true;
   }

   /**
    * Converts a {@link MomentumRateCommand} into a {@link MotionQPInput}.
    * @return true if the command was successfully converted.
    */
   public boolean convertMomentumRateCommand(MomentumRateCommand commandToConvert, MotionQPInput motionQPInputToPack)
   {
      DenseMatrix64F selectionMatrix = commandToConvert.getSelectionMatrix();
      int taskSize = selectionMatrix.getNumRows();

      if (taskSize == 0)
         return false;

      motionQPInputToPack.reshape(taskSize);
      motionQPInputToPack.setUseWeightScalar(false);
      motionQPInputToPack.setIsMotionConstraint(false);

      // Compute the weight: W = S * W * S^T
      tempTaskWeight.reshape(SpatialAccelerationVector.SIZE, SpatialAccelerationVector.SIZE);
      commandToConvert.getWeightMatrix(tempTaskWeight);
      tempTaskWeightSubspace.reshape(taskSize, SpatialAccelerationVector.SIZE);
      CommonOps.mult(selectionMatrix, tempTaskWeight, tempTaskWeightSubspace);
      CommonOps.multTransB(tempTaskWeightSubspace, selectionMatrix, motionQPInputToPack.taskWeightMatrix);

      // Compute the task Jacobian: J = S * A
      DenseMatrix64F centroidalMomentumMatrix = getCentroidalMomentumMatrix();
      CommonOps.mult(selectionMatrix, centroidalMomentumMatrix, motionQPInputToPack.taskJacobian);

      DenseMatrix64F momemtumRate = commandToConvert.getMomentumRate();
      DenseMatrix64F convectiveTerm = centroidalMomentumHandler.getCentroidalMomentumConvectiveTerm();

      // Compute the task objective: p = S * ( hDot - ADot qDot )
      CommonOps.subtract(momemtumRate, convectiveTerm, tempTaskObjective);
      CommonOps.mult(selectionMatrix, tempTaskObjective, motionQPInputToPack.taskObjective);

      tempTaskAlphaTaskPriority.reshape(taskSize, 1);
      CommonOps.mult(selectionMatrix, commandToConvert.getAlphaTaskPriorityVector(), tempTaskAlphaTaskPriority);

      for (int i = taskSize - 1; i >= 0; i--)
      {
         double alpha = tempTaskAlphaTaskPriority.get(i, 0);
         MatrixTools.scaleRow(alpha, i, motionQPInputToPack.taskJacobian);
         MatrixTools.scaleRow(alpha, i, motionQPInputToPack.taskObjective);
      }

      recordTaskJacobian(motionQPInputToPack.taskJacobian);

      return true;
   }

   /**
    * Converts a {@link MomentumCommand} into a {@link MotionQPInput}.
    * @return true if the command was successfully converted.
    */
   public boolean convertMomentumCommand(MomentumCommand commandToConvert, MotionQPInput motionQPInputToPack)
   {
      DenseMatrix64F selectionMatrix = commandToConvert.getSelectionMatrix();
      int taskSize = selectionMatrix.getNumRows();

      if (taskSize == 0)
         return false;

      motionQPInputToPack.reshape(taskSize);
      motionQPInputToPack.setUseWeightScalar(false);
      motionQPInputToPack.setIsMotionConstraint(false);

      // Compute the weight: W = S * W * S^T
      tempTaskWeight.reshape(SpatialAccelerationVector.SIZE, SpatialAccelerationVector.SIZE);
      commandToConvert.getWeightMatrix(tempTaskWeight);
      tempTaskWeightSubspace.reshape(taskSize, SpatialAccelerationVector.SIZE);
      CommonOps.mult(selectionMatrix, tempTaskWeight, tempTaskWeightSubspace);
      CommonOps.multTransB(tempTaskWeightSubspace, selectionMatrix, motionQPInputToPack.taskWeightMatrix);

      // Compute the task Jacobian: J = S * A
      DenseMatrix64F centroidalMomentumMatrix = getCentroidalMomentumMatrix();
      CommonOps.mult(selectionMatrix, centroidalMomentumMatrix, motionQPInputToPack.taskJacobian);

      DenseMatrix64F momemtum = commandToConvert.getMomentum();

      // Compute the task objective: p = S * h
      CommonOps.mult(selectionMatrix, momemtum, motionQPInputToPack.taskObjective);

      recordTaskJacobian(motionQPInputToPack.taskJacobian);

      return true;
   }

   /**
    * Converts a {@link JointspaceAccelerationCommand} into a {@link MotionQPInput}.
    * @return true if the command was successfully converted.
    */
   public boolean convertJointspaceAccelerationCommand(JointspaceAccelerationCommand commandToConvert, MotionQPInput motionQPInputToPack)
   {
      int taskSize = ScrewTools.computeDegreesOfFreedom(commandToConvert.getJoints());

      if (taskSize == 0)
         return false;

      motionQPInputToPack.reshape(taskSize);
      motionQPInputToPack.setIsMotionConstraint(!commandToConvert.getHasWeight());
      if (commandToConvert.getHasWeight())
      {
         motionQPInputToPack.setUseWeightScalar(true);
         motionQPInputToPack.setWeight(commandToConvert.getWeight());
      }

      motionQPInputToPack.taskJacobian.zero();

      int row = 0;
      for (int jointIndex = 0; jointIndex < commandToConvert.getNumberOfJoints(); jointIndex++)
      {
         InverseDynamicsJoint joint = commandToConvert.getJoint(jointIndex);
         int[] columns = jointIndexHandler.getJointIndices(joint);
         if (columns == null)
            return false;
         for (int column : columns)
            motionQPInputToPack.taskJacobian.set(row, column, 1.0);

         CommonOps.insert(commandToConvert.getDesiredAcceleration(jointIndex), motionQPInputToPack.taskObjective, row, 0);
         row += joint.getDegreesOfFreedom();
      }

      recordTaskJacobian(motionQPInputToPack.taskJacobian);

      return true;
   }

   /**
    * Converts a {@link JointspaceVelocityCommand} into a {@link MotionQPInput}.
    * @return true if the command was successfully converted.
    */
   public boolean convertJointspaceVelocityCommand(JointspaceVelocityCommand commandToConvert, MotionQPInput motionQPInputToPack)
   {
      int taskSize = ScrewTools.computeDegreesOfFreedom(commandToConvert.getJoints());

      if (taskSize == 0)
         return false;

      motionQPInputToPack.reshape(taskSize);
      motionQPInputToPack.setIsMotionConstraint(commandToConvert.isHardConstraint());
      if (!commandToConvert.isHardConstraint())
      {
         motionQPInputToPack.setUseWeightScalar(true);
         motionQPInputToPack.setWeight(commandToConvert.getWeight());
      }

      motionQPInputToPack.taskJacobian.zero();

      int row = 0;
      for (int jointIndex = 0; jointIndex < commandToConvert.getNumberOfJoints(); jointIndex++)
      {
         InverseDynamicsJoint joint = commandToConvert.getJoint(jointIndex);
         int[] columns = jointIndexHandler.getJointIndices(joint);
         if (columns == null)
            return false;
         for (int column : columns)
            motionQPInputToPack.taskJacobian.set(row, column, 1.0);

         CommonOps.insert(commandToConvert.getDesiredVelocity(jointIndex), motionQPInputToPack.taskObjective, row, 0);
         row += joint.getDegreesOfFreedom();
      }

      recordTaskJacobian(motionQPInputToPack.taskJacobian);

      return true;
   }

   public void computeJointAccelerationLimits(double absoluteMaximumJointAcceleration, DenseMatrix64F qDDotMinToPack, DenseMatrix64F qDDotMaxToPack)
   {
      CommonOps.fill(qDDotMinToPack, Double.NEGATIVE_INFINITY);
      CommonOps.fill(qDDotMaxToPack, Double.POSITIVE_INFINITY);

      for (int i = 0; i < oneDoFJoints.length; i++)
      {
         OneDoFJoint joint = oneDoFJoints[i];
         int index = jointIndexHandler.getOneDoFJointIndex(joint);
         double jointLimitLower = jointLowerLimits.get(index, 0);
         double jointLimitUpper = jointUpperLimits.get(index, 0);

         double qDDotMin = Double.NEGATIVE_INFINITY;
         double qDDotMax = Double.POSITIVE_INFINITY;

         if (!Double.isInfinite(jointLimitLower))
         {
            double qDotMin = (jointLimitLower - joint.getQ()) / controlDT;
            qDDotMin = (qDotMin - joint.getQd()) / controlDT;
            qDDotMin = MathTools.clipToMinMax(qDDotMin, -absoluteMaximumJointAcceleration, 0.0);
            qDDotMinToPack.set(index, 0, qDDotMin);
         }
         if (!Double.isInfinite(jointLimitUpper))
         {
            double qDotMax = (jointLimitUpper - joint.getQ()) / controlDT;
            qDDotMax = (qDotMax - joint.getQd()) / controlDT;
            qDDotMax = MathTools.clipToMinMax(qDDotMax, -0.0, absoluteMaximumJointAcceleration);
            qDDotMaxToPack.set(index, 0, qDDotMax);
         }
      }
   }

   public void computeJointVelocityLimits(DenseMatrix64F qDotMinToPack, DenseMatrix64F qDotMaxToPack)
   {
      CommonOps.fill(qDotMinToPack, Double.NEGATIVE_INFINITY);
      CommonOps.fill(qDotMaxToPack, Double.POSITIVE_INFINITY);

      for (int i = 0; i < oneDoFJoints.length; i++)
      {
         OneDoFJoint joint = oneDoFJoints[i];
         int index = jointIndexHandler.getOneDoFJointIndex(joint);
         double jointLimitLower = jointLowerLimits.get(index, 0);
         if (!Double.isInfinite(jointLimitLower))
            qDotMinToPack.set(index, 0, (jointLimitLower - joint.getQ()) / controlDT);
         double jointLimitUpper = jointUpperLimits.get(index, 0);
         if (!Double.isInfinite(jointLimitUpper))
            qDotMaxToPack.set(index, 0, (jointLimitUpper - joint.getQ()) / controlDT);
      }
   }

   private void recordTaskJacobian(DenseMatrix64F taskJacobian)
   {
      int taskSize = taskJacobian.getNumRows();
      allTaskJacobian.reshape(allTaskJacobian.getNumRows() + taskSize, numberOfDoFs);
      CommonOps.insert(taskJacobian, allTaskJacobian, allTaskJacobian.getNumRows() - taskSize, 0);
   }

   public DenseMatrix64F getCentroidalMomentumMatrix()
   {
      return centroidalMomentumHandler.getCentroidalMomentumMatrixPart(jointsToOptimizeFor);
   }

   public DenseMatrix64F getCentroidalMomentumConvectiveTerm()
   {
      return centroidalMomentumHandler.getCentroidalMomentumConvectiveTerm();
   }

   public SpatialForceVector computeCentroidalMomentumRateFromSolution(DenseMatrix64F jointAccelerations)
   {
      centroidalMomentumHandler.computeCentroidalMomentumRate(jointsToOptimizeFor, jointAccelerations);
      return centroidalMomentumHandler.getCentroidalMomentumRate();
   }
}
