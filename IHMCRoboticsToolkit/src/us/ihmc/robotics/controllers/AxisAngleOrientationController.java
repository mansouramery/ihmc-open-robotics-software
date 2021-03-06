package us.ihmc.robotics.controllers;

import javax.vecmath.AxisAngle4d;
import javax.vecmath.Matrix3d;
import javax.vecmath.Quat4d;

import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.geometry.AngleTools;
import us.ihmc.robotics.geometry.FrameOrientation;
import us.ihmc.robotics.geometry.FrameVector;
import us.ihmc.robotics.math.filters.RateLimitedYoFrameVector;
import us.ihmc.robotics.math.frames.YoFrameVector;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;

public class AxisAngleOrientationController
{
   private final YoVariableRegistry registry;

   private final YoFrameVector rotationErrorInBody;
   private final YoFrameVector rotationErrorCumulated;
   private final YoFrameVector velocityError;

   private final Matrix3d proportionalGainMatrix;
   private final Matrix3d derivativeGainMatrix;
   private final Matrix3d integralGainMatrix;

   private final ReferenceFrame bodyFrame;
   private final FrameVector proportionalTerm;
   private final FrameVector derivativeTerm;
   private final FrameVector integralTerm;

   private final AxisAngle4d errorAngleAxis = new AxisAngle4d();
   private final Quat4d errorQuaternion = new Quat4d();

   private final YoFrameVector feedbackAngularAction;
   private final RateLimitedYoFrameVector rateLimitedFeedbackAngularAction;

   private final double dt;

   private final YoOrientationPIDGainsInterface gains;

   public AxisAngleOrientationController(String prefix, ReferenceFrame bodyFrame, double dt, YoVariableRegistry parentRegistry)
   {
      this(prefix, bodyFrame, dt, null, parentRegistry);
   }

   public AxisAngleOrientationController(String prefix, ReferenceFrame bodyFrame, double dt, YoOrientationPIDGainsInterface gains,
         YoVariableRegistry parentRegistry)
   {
      this.dt = dt;
      this.bodyFrame = bodyFrame;
      registry = new YoVariableRegistry(prefix + getClass().getSimpleName());

      if (gains == null)
         gains = new YoAxisAngleOrientationGains(prefix, registry);

      this.gains = gains;
      proportionalGainMatrix = gains.createProportionalGainMatrix();
      derivativeGainMatrix = gains.createDerivativeGainMatrix();
      integralGainMatrix = gains.createIntegralGainMatrix();

      rotationErrorInBody = new YoFrameVector(prefix + "RotationErrorInBody", bodyFrame, registry);
      rotationErrorCumulated = new YoFrameVector(prefix + "RotationErrorCumulated", bodyFrame, registry);
      velocityError = new YoFrameVector(prefix + "AngularVelocityError", bodyFrame, registry);

      proportionalTerm = new FrameVector(bodyFrame);
      derivativeTerm = new FrameVector(bodyFrame);
      integralTerm = new FrameVector(bodyFrame);

      feedbackAngularAction = new YoFrameVector(prefix + "FeedbackAngularAction", bodyFrame, registry);
      rateLimitedFeedbackAngularAction = RateLimitedYoFrameVector.createRateLimitedYoFrameVector(prefix + "RateLimitedFeedbackAngularAction", "",
            registry, gains.getYoMaximumFeedbackRate(), dt, feedbackAngularAction);

      parentRegistry.addChild(registry);
   }

   public void reset()
   {
      rateLimitedFeedbackAngularAction.reset();
   }

   public void resetIntegrator()
   {
      rotationErrorCumulated.setToZero();
   }

   public void compute(FrameVector output, FrameOrientation desiredOrientation, FrameVector desiredAngularVelocity, FrameVector currentAngularVelocity,
         FrameVector feedForward)
   {
      computeProportionalTerm(desiredOrientation);
      if (currentAngularVelocity != null)
         computeDerivativeTerm(desiredAngularVelocity, currentAngularVelocity);
      computeIntegralTerm();

      output.setToZero(proportionalTerm.getReferenceFrame());
      output.add(proportionalTerm);
      output.add(derivativeTerm);
      output.add(integralTerm);

      // Limit the max acceleration of the feedback, but not of the feedforward...
      // JEP changed 150430 based on Atlas hitting limit stops.
      double feedbackAngularActionMagnitude = output.length();
      double maximumAction = gains.getMaximumFeedback();
      if (feedbackAngularActionMagnitude > maximumAction)
      {
         output.scale(maximumAction / feedbackAngularActionMagnitude);
      }

      feedbackAngularAction.set(output);
      rateLimitedFeedbackAngularAction.update();
      rateLimitedFeedbackAngularAction.getFrameTuple(output);

      feedForward.changeFrame(bodyFrame);
      output.add(feedForward);
   }

   private void computeProportionalTerm(FrameOrientation desiredOrientation)
   {
      desiredOrientation.changeFrame(bodyFrame);
      desiredOrientation.getQuaternion(errorQuaternion);
      errorAngleAxis.set(errorQuaternion);
      errorAngleAxis.setAngle(AngleTools.trimAngleMinusPiToPi(errorAngleAxis.getAngle()));

      // Limit the maximum position error considered for control action
      double maximumError = gains.getMaximumProportionalError();
      if (errorAngleAxis.getAngle() > maximumError)
      {
         errorAngleAxis.setAngle(Math.signum(errorAngleAxis.getAngle()) * maximumError);
      }

      proportionalTerm.set(errorAngleAxis.getX(), errorAngleAxis.getY(), errorAngleAxis.getZ());
      proportionalTerm.scale(errorAngleAxis.getAngle());
      rotationErrorInBody.set(proportionalTerm);

      proportionalGainMatrix.transform(proportionalTerm.getVector());
   }

   private void computeDerivativeTerm(FrameVector desiredAngularVelocity, FrameVector currentAngularVelocity)
   {
      desiredAngularVelocity.changeFrame(bodyFrame);
      currentAngularVelocity.changeFrame(bodyFrame);

      derivativeTerm.sub(desiredAngularVelocity, currentAngularVelocity);

      // Limit the maximum velocity error considered for control action
      double maximumVelocityError = gains.getMaximumDerivativeError();
      double velocityErrorMagnitude = derivativeTerm.length();
      if (velocityErrorMagnitude > maximumVelocityError)
      {
         derivativeTerm.scale(maximumVelocityError / velocityErrorMagnitude);
      }

      velocityError.set(derivativeTerm);
      derivativeGainMatrix.transform(derivativeTerm.getVector());
   }

   private void computeIntegralTerm()
   {
      if (gains.getMaximumIntegralError() < 1e-5)
      {
         integralTerm.setToZero(bodyFrame);
         return;
      }

      double integratedErrorAngle = errorAngleAxis.getAngle() * dt;
      double errorIntegratedX = errorAngleAxis.getX() * integratedErrorAngle;
      double errorIntegratedY = errorAngleAxis.getY() * integratedErrorAngle;
      double errorIntegratedZ = errorAngleAxis.getZ() * integratedErrorAngle;
      rotationErrorCumulated.add(errorIntegratedX, errorIntegratedY, errorIntegratedZ);

      double errorMagnitude = rotationErrorCumulated.length();
      if (errorMagnitude > gains.getMaximumIntegralError())
      {
         rotationErrorCumulated.scale(gains.getMaximumIntegralError() / errorMagnitude);
      }

      rotationErrorCumulated.getFrameTuple(integralTerm);
      integralGainMatrix.transform(integralTerm.getVector());
   }

   public void setProportionalGains(double proportionalGainX, double proportionalGainY, double proportionalGainZ)
   {
      gains.setProportionalGains(proportionalGainX, proportionalGainY, proportionalGainZ);
   }

   public void setDerivativeGains(double derivativeGainX, double derivativeGainY, double derivativeGainZ)
   {
      gains.setDerivativeGains(derivativeGainX, derivativeGainY, derivativeGainZ);
   }

   public void setIntegralGains(double integralGainX, double integralGainY, double integralGainZ, double maxIntegralError)
   {
      gains.setIntegralGains(integralGainX, integralGainY, integralGainZ, maxIntegralError);
   }

   public void setProportionalGains(double[] proportionalGains)
   {
      gains.setProportionalGains(proportionalGains);
   }

   public void setDerivativeGains(double[] derivativeGains)
   {
      gains.setDerivativeGains(derivativeGains);
   }

   public void setIntegralGains(double[] integralGains, double maxIntegralError)
   {
      gains.setIntegralGains(integralGains, maxIntegralError);
   }

   public void setMaxFeedbackAndFeedbackRate(double maxFeedback, double maxFeedbackRate)
   {
      gains.setMaxFeedbackAndFeedbackRate(maxFeedback, maxFeedbackRate);
   }

   public void setMaxDerivativeError(double maxDerivativeError)
   {
      gains.setMaxDerivativeError(maxDerivativeError);
   }

   public void setMaxProportionalError(double maxProportionalError)
   {
      gains.setMaxProportionalError(maxProportionalError);
   }

   public void setGains(OrientationPIDGainsInterface gains)
   {
      this.gains.set(gains);
   }
}
