package us.ihmc.darpaRoboticsChallenge.networkProcessor.depthData;

import java.util.ArrayList;
import java.util.Arrays;

import javax.vecmath.Point3d;

import sensor_msgs.PointCloud2;
import us.ihmc.sensorProcessing.parameters.DRCRobotPointCloudParameters;
import us.ihmc.utilities.math.geometry.ReferenceFrame;
import us.ihmc.utilities.ros.RosMainNode;
import us.ihmc.utilities.ros.subscriber.RosPointCloudSubscriber;

public class RosPointCloudReceiver extends RosPointCloudSubscriber
{
   private final ReferenceFrame cloudFrame;
   private final PointCloudDataReceiver pointCloudDataReceiver;
   private final ReferenceFrame sensorframe;
   private final PointCloudSource[] pointCloudSource;

   public RosPointCloudReceiver(String sensorNameInSdf, String rosTopic, RosMainNode rosMainNode, ReferenceFrame scanFrame,
         PointCloudDataReceiver pointCloudDataReceiver, PointCloudSource... pointCloudSource)
   {
      this.cloudFrame = scanFrame;
      this.pointCloudDataReceiver = pointCloudDataReceiver;
      this.sensorframe = pointCloudDataReceiver.getLidarFrame(sensorNameInSdf);
      this.pointCloudSource = pointCloudSource;


      rosMainNode.attachSubscriber(rosTopic, this);

   }
   
   public RosPointCloudReceiver(DRCRobotPointCloudParameters pointCloudParameters, RosMainNode rosMainNode, ReferenceFrame cloudFrame,
         PointCloudDataReceiver pointCloudDataReceiver, PointCloudSource... pointCloudSource)
   {
      this.cloudFrame = cloudFrame;
      this.pointCloudDataReceiver = pointCloudDataReceiver;
      this.sensorframe = pointCloudDataReceiver.getLidarFrame(pointCloudParameters.getSensorNameInSdf());
      this.pointCloudSource=pointCloudSource;

      rosMainNode.attachSubscriber(pointCloudParameters.getRosTopic(), this);
   }


   @Override
	public void onNewMessage(PointCloud2 pointCloud) 
	{
		UnpackedPointCloud pointCloudData = unpackPointsAndIntensities(pointCloud);
		Point3d[] points = pointCloudData.getPoints();
		ArrayList<Point3d> pointsAsArrayList = new ArrayList<Point3d>(Arrays.asList(points));
	   long[] timestamps = new long[points.length];
	   long time = pointCloud.getHeader().getStamp().totalNsecs();
	   Arrays.fill(timestamps, time);
		pointCloudDataReceiver.receivedPointCloudData(cloudFrame, sensorframe, timestamps, pointsAsArrayList, pointCloudSource);
	}
}