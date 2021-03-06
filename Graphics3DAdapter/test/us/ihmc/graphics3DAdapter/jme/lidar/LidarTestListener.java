package us.ihmc.graphics3DAdapter.jme.lidar;

import us.ihmc.robotics.lidar.LidarScan;

public interface LidarTestListener
{
   public void notify(LidarScan gpuScan, LidarScan traceScan);

   public void stop();
}
