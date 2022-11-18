object RunJobs {
  def main(args: Array[String]): Unit = {
    //VmAllocation.runVmAllocation
    //VmUtilAndSchedule.runVmScheduler
    CloudProvider.runCloud
    //ScalingDatacenter.runScaling
  }

}
