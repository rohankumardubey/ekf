package us.ihmc.ekf.robots;

import us.ihmc.simulationconstructionset.SimulationConstructionSet;

public class SimpleArmSimulation
{
   private static final double dt = 0.001;
   private final SimulationConstructionSet scs;

   public SimpleArmSimulation()
   {
      SimpleArmRobot simpleArmRobot = new SimpleArmRobot();

      scs = new SimulationConstructionSet(simpleArmRobot.getRobot());
      scs.setDT(dt, 1);
      scs.startOnAThread();
      scs.simulate(4.0);
   }

   public static void main(String[] args)
   {
      new SimpleArmSimulation();
   }
}
