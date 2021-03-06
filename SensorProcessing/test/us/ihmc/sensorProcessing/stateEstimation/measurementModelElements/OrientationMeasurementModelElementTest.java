package us.ihmc.sensorProcessing.stateEstimation.measurementModelElements;

import java.util.Random;

import javax.vecmath.Matrix3d;
import javax.vecmath.Vector3d;

import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.EjmlUnitTests;
import org.junit.Test;

import us.ihmc.controlFlow.ControlFlowElement;
import us.ihmc.controlFlow.ControlFlowInputPort;
import us.ihmc.controlFlow.ControlFlowOutputPort;
import us.ihmc.controlFlow.NullControlFlowElement;
import us.ihmc.tools.testing.TestPlanAnnotations.DeployableTestMethod;
import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.geometry.FrameOrientation;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.geometry.RigidBodyTransform;
import us.ihmc.robotics.random.RandomTools;
import us.ihmc.robotics.screwTheory.RigidBody;
import us.ihmc.robotics.screwTheory.ScrewTestTools.RandomFloatingChain;
import us.ihmc.sensorProcessing.stateEstimation.measurementModelElements.OrientationMeasurementModelElement;
import us.ihmc.robotics.screwTheory.SixDoFJoint;

public class OrientationMeasurementModelElementTest
{
   private static final Vector3d X = new Vector3d(1.0, 0.0, 0.0);
   private static final Vector3d Y = new Vector3d(0.0, 1.0, 0.0);
   private static final Vector3d Z = new Vector3d(0.0, 0.0, 1.0);

	@DeployableTestMethod(estimatedDuration = 0.0)
	@Test(timeout = 30000)
   public void test()
   {
      Random random = new Random(125125523L);
      Vector3d[] jointAxes = new Vector3d[] { X, Y, Z };
      RandomFloatingChain randomFloatingChain = new RandomFloatingChain(random, jointAxes);
      final RigidBody elevator = randomFloatingChain.getElevator();
      final SixDoFJoint rootJoint = randomFloatingChain.getRootJoint();

      ReferenceFrame estimationFrame = randomFloatingChain.getRootJoint().getFrameAfterJoint();
      RigidBody measurementLink = randomFloatingChain.getRevoluteJoints().get(jointAxes.length - 1).getSuccessor();
      ReferenceFrame measurementFrame = measurementLink.getParentJoint().getFrameAfterJoint(); // measurementLink.getBodyFixedFrame();

      ControlFlowElement controlFlowElement = new NullControlFlowElement();

      final ControlFlowOutputPort<FrameOrientation> orientationPort = new ControlFlowOutputPort<FrameOrientation>("orientationPort", controlFlowElement);
      ControlFlowInputPort<Matrix3d> orientationMeasurementInputPort = new ControlFlowInputPort<Matrix3d>("orientationMeasurementInputPort", controlFlowElement);
      String name = "test";
      YoVariableRegistry registry = new YoVariableRegistry(name);

      OrientationMeasurementModelElement modelElement = new OrientationMeasurementModelElement(orientationPort, orientationMeasurementInputPort,
            estimationFrame, measurementFrame, name, registry);

      Matrix3d orientation = new Matrix3d();
      orientation.set(RandomTools.generateRandomRotation(random));
      orientationPort.setData(new FrameOrientation(ReferenceFrame.getWorldFrame(), orientation));

      randomFloatingChain.setRandomPositionsAndVelocities(random);

      Runnable orientationUpdater = new Runnable()
      {

         public void run()
         {
            rootJoint.setRotation(orientationPort.getData().getMatrix3dCopy());
            elevator.updateFramesRecursively();
         }
      };
      orientationUpdater.run();

      RigidBodyTransform transformFromMeasurementToWorld = measurementFrame.getTransformToDesiredFrame(elevator.getBodyFixedFrame());
      Matrix3d rotationFromMeasurementToWorld = new Matrix3d();
      transformFromMeasurementToWorld.getRotation(rotationFromMeasurementToWorld);
      orientationMeasurementInputPort.setData(rotationFromMeasurementToWorld);

      DenseMatrix64F zeroResidual = modelElement.computeResidual();
      DenseMatrix64F zeroVector = new DenseMatrix64F(3, 1);
      EjmlUnitTests.assertEquals(zeroVector, zeroResidual, 1e-12);

      double perturbation = 1e-3;
      double tol = 1e-11;
      modelElement.computeMatrixBlocks();

      // orientation perturbations
      MeasurementModelTestTools.assertOutputMatrixCorrectUsingPerturbation(orientationPort, modelElement, new FrameOrientation(orientationPort.getData()),
            perturbation, tol, orientationUpdater);
   }
}
