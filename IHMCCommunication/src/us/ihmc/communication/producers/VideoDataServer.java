package us.ihmc.communication.producers;

import java.awt.image.BufferedImage;

import javax.vecmath.Point3d;
import javax.vecmath.Quat4d;

import boofcv.struct.calib.IntrinsicParameters;

public interface VideoDataServer
{
   public abstract void updateImage(VideoSource videoSource, BufferedImage bufferedImage, long timeStamp, Point3d cameraPosition, Quat4d cameraOrientation, IntrinsicParameters intrinsicParameters);

   public abstract void close();

   public abstract boolean isConnected();
}
