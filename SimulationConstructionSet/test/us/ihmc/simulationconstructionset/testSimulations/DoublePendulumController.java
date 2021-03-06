package us.ihmc.simulationconstructionset.testSimulations;

import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.dataStructures.variable.LongYoVariable;
import us.ihmc.robotics.time.TimeTools;
import us.ihmc.simulationconstructionset.robotController.MultiThreadedRobotControlElement;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicsListRegistry;

public class DoublePendulumController implements MultiThreadedRobotControlElement
{
   private final YoVariableRegistry registry = new YoVariableRegistry("DoublePendulumController");
   private final DoubleYoVariable q_j1 = new DoubleYoVariable("q_j1", registry);
   private final DoubleYoVariable qd_j1 = new DoubleYoVariable("qd_j1", registry);
   private final DoubleYoVariable q_j2 = new DoubleYoVariable("q_j2", registry);
   private final DoubleYoVariable qd_j2 = new DoubleYoVariable("qd_j2", registry);

   private final DoubleYoVariable tau_j1 = new DoubleYoVariable("tau_j1", registry);
   private final DoubleYoVariable tau_j2 = new DoubleYoVariable("tau_j2", registry);

   private final DoubleYoVariable q_j1_d = new DoubleYoVariable("q_j1_d", registry);
   private final DoubleYoVariable q_j2_d = new DoubleYoVariable("q_j2_d", registry);

   private final DoubleYoVariable kp = new DoubleYoVariable("kp", registry);
   private final DoubleYoVariable kd = new DoubleYoVariable("kd", registry);
   
   private final LongYoVariable tick = new LongYoVariable("tick", registry);

   private final DoublePendulum doublePendulum;

   public DoublePendulumController(DoublePendulum doublePendulum)
   {
      this.doublePendulum = doublePendulum;
   }

   public void initialize()
   {
      this.kp.set(100.0);
      this.kd.set(10.0);
      
      doublePendulum.getJ1().setQ(0.1);
   }

   public void read(long currentClockTime)
   {
      q_j1.set(doublePendulum.getJ1().getQ().getDoubleValue());
      qd_j1.set(doublePendulum.getJ1().getQD().getDoubleValue());
      q_j2.set(doublePendulum.getJ2().getQ().getDoubleValue());
      qd_j2.set(doublePendulum.getJ2().getQD().getDoubleValue());
   }

   public void run()
   {
      q_j1_d.set(q_j1_d.getDoubleValue() + 0.1);
      q_j2_d.set(q_j2_d.getDoubleValue() + 0.05);
      
      tau_j1.set(kp.getDoubleValue() * (q_j1_d.getDoubleValue() - q_j1.getDoubleValue()) + kd.getDoubleValue() * (-qd_j1.getDoubleValue()));
      tau_j2.set(kp.getDoubleValue() * (q_j2_d.getDoubleValue() - q_j2.getDoubleValue()) + kd.getDoubleValue() * (-qd_j2.getDoubleValue()));
      
      tick.increment();
      
      long start = System.nanoTime();
      while(System.nanoTime() - start < TimeTools.milliSecondsToNanoSeconds(10))
      {
         // Busy wait
      }
   }

   public void write(long timestamp)
   {
      doublePendulum.getJ1().setTau(tau_j1.getDoubleValue());
      doublePendulum.getJ2().setTau(tau_j2.getDoubleValue());
   }

   public YoVariableRegistry getYoVariableRegistry()
   {
      return registry;
   }

   public String getName()
   {
      return getClass().getSimpleName();
   }

   public YoGraphicsListRegistry getDynamicGraphicObjectsListRegistry()
   {
      return null;
   }

   public long nextWakeupTime()
   {
      return 0;
   }

}
