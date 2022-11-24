import CreateDatacenter.CreateObjects
import HelperUtils.CreateLogger
import org.cloudbus.cloudsim.allocationpolicies.{VmAllocationPolicyFirstFit, VmAllocationPolicyRoundRobin}
import org.cloudbus.cloudsim.brokers.{DatacenterBroker, DatacenterBrokerSimple}
import org.cloudsimplus.builders.tables.{CloudletsTableBuilder, TextTableColumn}

import scala.jdk.CollectionConverters.*
import org.cloudbus.cloudsim.cloudlets.Cloudlet
import org.cloudbus.cloudsim.cloudlets.CloudletSimple
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.datacenters.Datacenter
import org.cloudbus.cloudsim.datacenters.DatacenterSimple
import org.cloudbus.cloudsim.hosts.Host
import org.cloudbus.cloudsim.hosts.HostSimple
import org.cloudbus.cloudsim.resources.Pe
import org.cloudbus.cloudsim.resources.PeSimple
import org.cloudbus.cloudsim.utilizationmodels.{UtilizationModel, UtilizationModelDynamic, UtilizationModelFull, UtilizationModelStochastic}
import org.cloudbus.cloudsim.vms.Vm
import org.cloudbus.cloudsim.vms.VmSimple
import org.cloudbus.cloudsim.schedulers.vm
import org.cloudbus.cloudsim.schedulers.vm.{VmScheduler, VmSchedulerSpaceShared, VmSchedulerTimeShared}
import com.typesafe.config.{Config, ConfigFactory}

import java.util

/**
 * This program compares different VmAllocation policies by running two simluations
 */
object VmAllocation {
  val logger = CreateLogger(classOf[VmAllocation.type])
  def runVmAllocation: Unit = {
    //Getting our configs
    logger.info("*****-----------------------------------------Starting VmAllocation Simulation-----------------------------------------*****")
    val config: Config = ConfigFactory.load("VmAllocation.conf")
    val hostconfig = config.getConfig("HostConfig")
    val vmconfig = config.getConfig("VMConfig")
    val cloudletconfig = config.getConfig("CloudletConfig")
    val dataconfig = config.getConfig("DatacenterConfig")
    //starting a new simulation
    val simulation = new CloudSim
    //creating a new datacenter
    val datacenter0 = CreateObjects.createDatacenter( new VmSchedulerTimeShared, hostconfig.getInt("HOSTS"), hostconfig.getInt("HOST_PES"), hostconfig.getInt("HOST_MIPS"), hostconfig.getInt("HOST_RAM"), hostconfig.getInt("HOST_BW"), hostconfig.getInt("HOST_STORAGE"), simulation,dataconfig)
    logger.info("Datacenter0 created")
    //creating a new broker to manage our VMs and Cloudlets
    val broker0 = new DatacenterBrokerSimple(simulation)
    logger.info("Broker0 created")
    //creating our VMS
    val vmList = CreateObjects.createVms(hostconfig.getInt("HOST_MIPS"), vmconfig.getInt("VMS"), vmconfig.getInt("VM_PES"), vmconfig.getInt("VM_RAM"), vmconfig.getInt("VM_BW"), vmconfig.getInt("VM_SIZE"))
    //copying our VMs to use later for the second simulation
    val vmList1 = vmList
    logger.info("VMs created")
    //creating our new cloudlets
    val cloudletList = CreateObjects.createCloudlets(new UtilizationModelDynamic(0.5), cloudletconfig.getInt("CLOUDLETS"), cloudletconfig.getInt("CLOUDLET_LENGTH"), cloudletconfig.getInt("CLOUDLET_PES"), cloudletconfig.getInt("CLOUDLET_SIZE"))
    logger.info("Cloudlets created")
    //submitting our VMs
    broker0.submitVmList(vmList)
    logger.info("VMs submitted")
    //setting our datacenter to use the First Fit policy for VM allocation.
    datacenter0.setVmAllocationPolicy(new VmAllocationPolicyFirstFit)
    logger.info("Simulating first datacenter using VmAllocationPolicyFirstFit")
    //submitting our cloudlets
    broker0.submitCloudletList(cloudletList)
    //starting the first simulation
    simulation.start
    //getting our results
    val finishedCloudlets = broker0.getCloudletFinishedList.asScala
    //outputting our results as a table
    new CloudletsTableBuilder(finishedCloudlets.asJava).addColumn(new TextTableColumn("RAM Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfRam() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
      .addColumn(new TextTableColumn("CPU Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfCpu() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
      .build()
    logger.info("Starting execution of second datacenter")
    /**
     *
     *  Setting up everything for our second simulation
     *
     */
    //creating our second simulation
    val simulation1 = new CloudSim
    //creating the second datacenter
    val datacenter1 = CreateObjects.createDatacenter( new VmSchedulerTimeShared,hostconfig.getInt("HOSTS"), hostconfig.getInt("HOST_PES"), hostconfig.getInt("HOST_MIPS"), hostconfig.getInt("HOST_RAM"), hostconfig.getInt("HOST_BW"), hostconfig.getInt("HOST_STORAGE"), simulation1,dataconfig)
    logger.info("Second datacenter created")
    //creating a second broker to manage our VMs and Cloudlets for the second simulation
    val broker1 = new DatacenterBrokerSimple(simulation1)
    logger.info("second broker created")
    //creating our new cloudlets
    val cloudletList1 = CreateObjects.createCloudlets(new UtilizationModelDynamic(0.5), cloudletconfig.getInt("CLOUDLETS"), cloudletconfig.getInt("CLOUDLET_LENGTH"), cloudletconfig.getInt("CLOUDLET_PES"), cloudletconfig.getInt("CLOUDLET_SIZE"))
    logger.info("New cloudlets created for second datacenter")
    //submitting our VMs to the second simulation
    broker1.submitVmList(vmList1)
    logger.info("VMs submitted to second datacenter")
    //setting our second datacenter to also use the First Fit policy for VM allocation.
    datacenter1.setVmAllocationPolicy(new VmAllocationPolicyRoundRobin)
    logger.info("Simulating first datacenter using VmAllocationPolicyRoundRobin")
    //submitting our cloudlets
    broker1.submitCloudletList(cloudletList1)
    logger.info("Cloudlets submitted to second datacenter")
    //starting the second simulation
    simulation1.start
    //gathering the results of our second simulation
    val finishedCloudlets1 = broker1.getCloudletFinishedList
    //outputting the results as a table
    new CloudletsTableBuilder(finishedCloudlets1).addColumn(new TextTableColumn("RAM Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfRam() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
      .addColumn(new TextTableColumn("CPU Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfCpu() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
      .build()
    logger.info("Simulation finished")
  }
}