import CreateDatacenter.CreateObjects
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
import org.cloudbus.cloudsim.vms.{Vm, VmCost, VmSimple}
import org.cloudbus.cloudsim.schedulers.vm
import org.cloudbus.cloudsim.schedulers.vm.{VmScheduler, VmSchedulerSpaceShared, VmSchedulerTimeShared}
import com.typesafe.config.{Config, ConfigFactory}

import java.util
import scala.language.postfixOps
import scala.util.Random

object VmUtilAndSchedule {
  var datacenter0: DatacenterSimple =_
  var datacenter1: DatacenterSimple =_
  def runVmScheduler: Unit = {
    //Getting our configs
    val config: Config = ConfigFactory.load("VmUtilAndSchedule.conf")
    val hostconfig = config.getConfig("HostConfig")
    val vmconfig = config.getConfig("VMConfig")
    val cloudletconfig = config.getConfig("CloudletConfig")
    val dataconfig: Config = config.getConfig("DatacenterConfig")
    //starting a new simulation
    val simulation = new CloudSim
    //creating a new datacenter
    if(vmconfig.getString("VM_SCHEDULER_1").equals("spaceshared")){
      datacenter0 = CreateObjects.createDatacenter(new VmSchedulerSpaceShared, hostconfig.getInt("HOSTS"), hostconfig.getInt("HOST_PES"), hostconfig.getInt("HOST_MIPS"), hostconfig.getInt("HOST_RAM"), hostconfig.getInt("HOST_BW"), hostconfig.getInt("HOST_STORAGE"), simulation)
    }
    else if(vmconfig.getString("VM_SCHEDULER_1").equals("timeshared"))
      {
        datacenter0 = CreateObjects.createDatacenter(new VmSchedulerTimeShared(), hostconfig.getInt("HOSTS"), hostconfig.getInt("HOST_PES"), hostconfig.getInt("HOST_MIPS"), hostconfig.getInt("HOST_RAM"), hostconfig.getInt("HOST_BW"), hostconfig.getInt("HOST_STORAGE"), simulation)
      }
      else{
      //default
      datacenter0 = CreateObjects.createDatacenter(new VmSchedulerSpaceShared, hostconfig.getInt("HOSTS"), hostconfig.getInt("HOST_PES"), hostconfig.getInt("HOST_MIPS"), hostconfig.getInt("HOST_RAM"), hostconfig.getInt("HOST_BW"), hostconfig.getInt("HOST_STORAGE"), simulation)
    }
    //creating a new broker to manage our VMs and Cloudlets
    val broker0 = new DatacenterBrokerSimple(simulation)
    //creating our VMS
    val vmList = CreateObjects.createVms(hostconfig.getInt("HOST_MIPS"), vmconfig.getInt("VMS"), vmconfig.getInt("VM_PES"), vmconfig.getInt("VM_RAM"), vmconfig.getInt("VM_BW"), vmconfig.getInt("VM_SIZE"))
    //copying our VMs to use later for the second simulation
    val vmList1 = vmList
    //creating our new cloudlets
    val cloudletList = CreateObjects.createCloudlets(new UtilizationModelStochastic, cloudletconfig.getInt("CLOUDLETS"), cloudletconfig.getInt("CLOUDLET_LENGTH"), cloudletconfig.getInt("CLOUDLET_PES"), cloudletconfig.getInt("CLOUDLET_SIZE"))
    //submitting our VMs
    broker0.submitVmList(vmList)
    //setting our datacenter to use the First Fit policy for VM allocation.
    datacenter0.setVmAllocationPolicy(new VmAllocationPolicyFirstFit)
    //datacenter0.getCharacteristics.setCostPerBw(0.02).setCostPerMem(0.008).setCostPerSecond(0.02).setCostPerStorage(0.0002)
    datacenter0.getCharacteristics.setCostPerBw(dataconfig.getDouble("COSTPERBW")).setCostPerMem(dataconfig.getDouble("COSTPERMEM")).setCostPerSecond(dataconfig.getDouble("COSTPERSEC")).setCostPerStorage(dataconfig.getDouble("COSTBYSTORAGE"))
    //submitting our cloudlets
    broker0.submitCloudletList(cloudletList)
    //starting the first simulation
    simulation.start
    //getting our results
    println("Simulation using VmSchedulerSpaceShared and UtilizationModelStochastic")
    val finishedCloudlets = broker0.getCloudletFinishedList.asScala
    //outputting our results as a table
    new CloudletsTableBuilder(finishedCloudlets.asJava).addColumn(new TextTableColumn("RAM Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfRam() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
      .addColumn(new TextTableColumn("CPU Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfCpu() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
      .build()
    println("\n-------------------------------------------\n")
    var totalcost = 0.0
    var processingtotalcost = 0.0
    var memorycost = 0.0
    var storagecost = 0.0
    var bwtotalcost = 0.0
    val createdvms1 = broker0.getVmCreatedList.asScala
    createdvms1.foreach(e => {
      val cost = new VmCost(e)
      processingtotalcost += cost.getProcessingCost
      memorycost += cost.getMemoryCost
      storagecost += cost.getStorageCost
      bwtotalcost += cost.getBwCost
      totalcost += cost.getTotalCost
      println(cost)
    })
    /**
     *
     *  Setting up everything for our second simulation
     *
     */
    //creating our second simulation
    val simulation1 = new CloudSim
    //creating the second datacenter
    //var datacenter1: DatacenterSimple = _
    if (vmconfig.getString("VM_SCHEDULER_2").equals("spaceshared")) {
      datacenter1 = CreateObjects.createDatacenter(new VmSchedulerSpaceShared, hostconfig.getInt("HOSTS"), hostconfig.getInt("HOST_PES"), hostconfig.getInt("HOST_MIPS"), hostconfig.getInt("HOST_RAM"), hostconfig.getInt("HOST_BW"), hostconfig.getInt("HOST_STORAGE"), simulation1)
    }
    else if (vmconfig.getString("VM_SCHEDULER_2").equals("timeshared")) {
      datacenter1 = CreateObjects.createDatacenter(new VmSchedulerTimeShared(), hostconfig.getInt("HOSTS"), hostconfig.getInt("HOST_PES"), hostconfig.getInt("HOST_MIPS"), hostconfig.getInt("HOST_RAM"), hostconfig.getInt("HOST_BW"), hostconfig.getInt("HOST_STORAGE"), simulation1)
    }
    else {
      //default
      datacenter1 = CreateObjects.createDatacenter(new VmSchedulerSpaceShared, hostconfig.getInt("HOSTS"), hostconfig.getInt("HOST_PES"), hostconfig.getInt("HOST_MIPS"), hostconfig.getInt("HOST_RAM"), hostconfig.getInt("HOST_BW"), hostconfig.getInt("HOST_STORAGE"), simulation1)
    }
    //creating a second broker to manage our VMs and Cloudlets for the second simulation
    val broker1 = new DatacenterBrokerSimple(simulation1)
    //creating our new cloudlets
    val cloudletList1 = CreateObjects.createCloudlets(new UtilizationModelDynamic(0.5), cloudletconfig.getInt("CLOUDLETS"), cloudletconfig.getInt("CLOUDLET_LENGTH"), cloudletconfig.getInt("CLOUDLET_PES"), cloudletconfig.getInt("CLOUDLET_SIZE"))
    //submitting our VMs to the second simulation
    broker1.submitVmList(vmList1)
    //setting our second datacenter to also use the First Fit policy for VM allocation.
    datacenter1.setVmAllocationPolicy(new VmAllocationPolicyFirstFit)
    //datacenter1.getCharacteristics.setCostPerBw(0.02).setCostPerMem(0.008).setCostPerSecond(0.02).setCostPerStorage(0.0002)
    datacenter1.getCharacteristics.setCostPerBw(dataconfig.getDouble("COSTPERBW")).setCostPerMem(dataconfig.getDouble("COSTPERMEM")).setCostPerSecond(dataconfig.getDouble("COSTPERSEC")).setCostPerStorage(dataconfig.getDouble("COSTBYSTORAGE"))
    //submitting our cloudlets
    broker1.submitCloudletList(cloudletList1)
    //starting the second simulation
    simulation1.start
    //gathering the results of our second simulation
    println("Simulation using VmSchedulerTimeShared and UtilizationModelDynamic(0.5)")
    val finishedCloudlets1 = broker1.getCloudletFinishedList.asScala
    //outputting the results as a table
    new CloudletsTableBuilder(finishedCloudlets1.asJava).addColumn(new TextTableColumn("RAM Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfRam() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
      .addColumn(new TextTableColumn("CPU Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfCpu() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
      .build()
    println("\n-------------------------------------------\n")
    totalcost = 0.0
    processingtotalcost = 0.0
    memorycost = 0.0
    storagecost = 0.0
    bwtotalcost = 0.0
    val createdvms2 = broker0.getVmCreatedList.asScala
    createdvms2.foreach(e => {
      val cost = new VmCost(e)
      processingtotalcost += cost.getProcessingCost
      memorycost += cost.getMemoryCost
      storagecost += cost.getStorageCost
      bwtotalcost += cost.getBwCost
      totalcost += cost.getTotalCost
      println(cost)
    })
  }
}