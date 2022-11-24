object RunJobs {
  def main(args: Array[String]): Unit = {
    println("*-------------Cloud Simulator-------------*")
    println("Press [1] to run VmAllocation Simulation")
    println("Press [2] to run VmUtilAndSchedule Simulation")
    println("Press [3] to run CloudProvider Simulation")
    println("Press [4] to run ScalingDatacenter Simulation")
    println("Press [5] to run IaasPaasSaas Simulation")
    val a = scala.io.StdIn.readInt()
    a match{
      case 1 => VmAllocation.runVmAllocation
      case 2 => VmUtilAndSchedule.runVmScheduler
      case 3 => CloudProvider.runCloud
      case 4 => ScalingDatacenter.runScaling
      case 5 => IaasPaasSaas.runSim
      case _ => println("Invalid Input")
    }
    //For Docker File
/*    println("Simulation 1\n")
    VmAllocation.runVmAllocation
    println("Simulation 2\n")
    VmUtilAndSchedule.runVmScheduler
    println("Simulation 3\n")
    CloudProvider.runCloud
    println("Simulation 4\n")
    ScalingDatacenter.runScaling
    println("Simulation 5\n")
    IaasPaasSaas.runSim*/
  }

}
