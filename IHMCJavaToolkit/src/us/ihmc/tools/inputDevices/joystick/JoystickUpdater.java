package us.ihmc.tools.inputDevices.joystick;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;

import net.java.games.input.Controller;
import net.java.games.input.Event;
import net.java.games.input.EventQueue;

public class JoystickUpdater implements Runnable
{
   private static final boolean DEBUG = false;
   
   private final Controller joystickController;
   private final Object listnerConch = new Object();
   private final ArrayList<JoystickEventListener> listeners = new ArrayList<JoystickEventListener>();
   private final ArrayList<JoystickStatusListener> generalListenersList;
   
   private HashMap<String, Float> lastValues = new HashMap<String, Float>();
   private int pollIntervalMillis = 5;
   private boolean connected;
   private boolean threadRunning = false;

   public JoystickUpdater(Controller joystickController, ArrayList<JoystickStatusListener> generalListenersList)
   {
      this.joystickController = joystickController;
      this.generalListenersList = generalListenersList;
      
      connected = true;
   }
   
   public void addListener(JoystickEventListener listener)
   {
      synchronized (listnerConch)
      {
         listeners.add(listener);
      }
   }
   
   public void clearListeners()
   {
      synchronized (listnerConch)
      {
         listeners.clear();
      }
   }

   @Override
   public void run()
   {
      threadRunning = true;
      while (threadRunning)
      {
         joystickController.poll();
         EventQueue queue = joystickController.getEventQueue();
         Event event = new Event();

         while (queue.getNextEvent(event))
         {
            if (isNewValue(event))
            {
               if (DEBUG)
               {
                  System.out.println("event = " + event);
               }

               synchronized (listnerConch)
               {
                  try
                  {
                     for (JoystickEventListener listener : listeners)
                     {
                        listener.processEvent(event);
                     }
                  }
                  catch (ConcurrentModificationException e)
                  {
                     // Some listeners might not be notified.
                  }
               }

               for (JoystickStatusListener listener : generalListenersList)
               {
                  listener.updateConnectivity(connected);
               }
            }
         }

         try
         {
            Thread.sleep(pollIntervalMillis);
         }
         catch (InterruptedException e)
         {
            e.printStackTrace();
         }
      }
   }
   
   public void stopThread()
   {
      threadRunning = false;
   }

   private boolean isNewValue(Event event)
   {
      Float value = lastValues.get(event.getComponent().getName());
      if (value != null)
      {
         if (event.getValue() == value)
         {
            lastValues.put(event.getComponent().getName(), value);

            return false;
         }
      }

      lastValues.put(event.getComponent().getName(), event.getValue());

      return true;
   }

   public void setPollIntervalMillis(int pollIntervalMillis)
   {
      this.pollIntervalMillis = pollIntervalMillis;
   }
}