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
      case 5 => println("Not implemented yet")
      case _ => println("Invalid Input")
    }
  }

}
