import CreateDatacenter.CreateObjects
import HelperUtils.CreateLogger
import com.typesafe.config.{Config, ConfigFactory}
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicyRoundRobin
import org.cloudbus.cloudsim.brokers.{DatacenterBroker, DatacenterBrokerSimple}
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletSimple}
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.datacenters.DatacenterSimple
import org.cloudbus.cloudsim.datacenters.network.NetworkDatacenter
import org.cloudbus.cloudsim.hosts.{Host, HostSimple}
import org.cloudbus.cloudsim.power.models.PowerModelHost
import org.cloudbus.cloudsim.resources.{Pe, PeSimple, Processor}
import org.cloudbus.cloudsim.schedulers.vm.{VmScheduler, VmSchedulerTimeShared}
import org.cloudbus.cloudsim.utilizationmodels.{UtilizationModel, UtilizationModelFull, UtilizationModelStochastic}
import org.cloudbus.cloudsim.vms.{Vm, VmCost, VmSimple}
import org.cloudsimplus.autoscaling.resources.ResourceScalingInstantaneous
import org.cloudsimplus.autoscaling.{HorizontalVmScaling, HorizontalVmScalingSimple, VerticalVmScaling, VerticalVmScalingSimple}
import org.cloudsimplus.builders.tables.{CloudletsTableBuilder, CsvTable, TextTableColumn}
import org.cloudsimplus.listeners.EventInfo

import java.io.{File, PrintStream, PrintWriter}
import scala.jdk.CollectionConverters.*
import java.util
import java.util.function.Supplier
import scala.language.postfixOps
import scala.util.Random

object ScalingDatacenter {
  //setting up our lists and count of objects
   private val vmList = util.ArrayList[Vm]
   private val cloudletList = util.ArrayList[Cloudlet]
   private var createdVms: Int = 0
   private var createdCloudlets: Int =0
    private var broker: DatacenterBroker = _
  val logger = CreateLogger(classOf[ScalingDatacenter.type])
  def runScaling: Unit ={
    //load configs
    logger.info("*****-----------------------------------------Starting ScalingDatacenter Simulation-----------------------------------------*****")
    val config: Config = ConfigFactory.load("ScalingConfig.conf")
    val hostconfig = config.getConfig("HostConfig")
    val vmconfig = config.getConfig("VMConfig")
    val cloudletconfig = config.getConfig("CloudletConfig")
    val dataconfig: Config = config.getConfig("DatacenterConfig")
    logger.info("Configs loaded")
    //create a new simulation
    val scalingsimulation = new CloudSim
    //create a new datacenter
    val datacenter = CreateObjects.createDatacenter(new VmSchedulerTimeShared,hostconfig.getInt("HOSTS"), hostconfig.getInt("HOST_PES"), hostconfig.getInt("HOST_MIPS"), hostconfig.getInt("HOST_RAM"), hostconfig.getInt("HOST_BW"), hostconfig.getInt("HOST_STORAGE"), scalingsimulation, dataconfig)
    //set the scheduling interval
    datacenter.setSchedulingInterval(dataconfig.getInt("SCHEDULING_INTERVAL"))
    //set the costs
    datacenter.getCharacteristics.setCostPerBw(dataconfig.getDouble("COSTPERBW")).setCostPerMem(dataconfig.getDouble("COSTPERMEM")).setCostPerSecond(dataconfig.getDouble("COSTPERSEC")).setCostPerStorage(dataconfig.getDouble("COSTBYSTORAGE"))
    logger.debug("Datacenter created")
    //create a new broker
    broker = new DatacenterBrokerSimple(scalingsimulation)
    logger.debug("Broker created")
    //add a new listener which introduces more cloudlets as time goes on
    scalingsimulation.addOnClockTickListener(createNewCloudlets)
    logger.debug("Added listener to simulation")
    //Creates new VMs
    vmList.addAll(createScalableVms(hostconfig.getInt("HOST_MIPS"), vmconfig.getInt("VMS"), vmconfig.getInt("VM_PES"), vmconfig.getInt("VM_RAM"), vmconfig.getInt("VM_BW"), vmconfig.getInt("VM_SIZE")))
    logger.debug("VMs created")
    //creates new cloudlets
    createCloudletList(cloudletconfig.getInt("CLOUDLETS"), cloudletconfig.getInt("CLOUDLET_LENGTH"), cloudletconfig.getInt("CLOUDLET_PES"), cloudletconfig.getInt("CLOUDLET_SIZE"))
    logger.debug("Cloudlets Created")
    //submit the vmlist
    broker.submitVmList(vmList)
    broker.setVmDestructionDelay(vmconfig.getInt("VMDESTRUCTION"))
    logger.debug("VMList submitted")
    //submit the cloudlets
    broker.submitCloudletList(cloudletList)
    logger.debug("Cloudletlist submitted")
    logger.debug("--------------------Starting Simulation--------------------")
    scalingsimulation.start
    logger.debug("--------------------Simulation Finished--------------------")
    //gather the results of the simulation
    val finishedCloudlets = broker.getCloudletFinishedList
    new CloudletsTableBuilder(finishedCloudlets).addColumn(new TextTableColumn("RAM Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfRam() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
      .addColumn(new TextTableColumn("CPU Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfCpu() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
      .build()
    println("\n-------------------------------------------\n")
    var totalcost = 0.0
    var processingtotalcost = 0.0
    var memorycost = 0.0
    var storagecost = 0.0
    var bwtotalcost = 0.0
    val createdvms1 = broker.getVmCreatedList.asScala
    val file = new PrintWriter(new File("ScalingDatacenterCost.txt"))
    createdvms1.foreach(e => {
      val cost = new VmCost(e)
      processingtotalcost += cost.getProcessingCost
      memorycost += cost.getMemoryCost
      storagecost += cost.getStorageCost
      bwtotalcost += cost.getBwCost
      totalcost += cost.getTotalCost
      println(cost)
      file.write("Vm: " + cost.getVm.toString + "\tExecution Secs:" + cost.getVm.getTotalExecutionTime + "\tCPU Cost: " + cost.getProcessingCost.toString + "$\tRAM Cost: " + cost.getMemoryCost.toString + "$\tStorage Cost:" + cost.getStorageCost.toString + "$\tBW Cost:" + cost.getBwCost.toString + "$\tTotal Cost: " + cost.getTotalCost.toString + "$\n")
    })
    file.close()

    val filepower = new PrintWriter(new File("ScalingDatacenterPower.txt"))
    datacenter.getHostList.asScala.foreach(host => {
      val powerModel: PowerModelHost = host.getPowerModel
      filepower.write("Host: " + host.getId + "\tTotal uptime:" + host.getTotalUpTime + " | Startup Time: " + powerModel.getTotalStartupTime + " | Startup Power: " + powerModel.getTotalStartupPower + " | Shutdown Time: " + powerModel.getTotalShutDownTime + " | Shutdown Power: " + powerModel.getTotalShutDownPower + "\n")
    })
    filepower.close()
    val csv: CsvTable = new CsvTable()
    csv.setPrintStream(new PrintStream(new File("ScalingDatacenterStats.csv")))
    new CloudletsTableBuilder(finishedCloudlets, csv).addColumn(new TextTableColumn("RAM Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfRam() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
      .addColumn(new TextTableColumn("CPU Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfCpu() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
      .build()
    logger.info("Simulation data is available in ScalingDatacenterStats.csv")
    logger.info("Cost data is available in ScalingDatacenterCost.txt")
    logger.info("Power usage data is available in ScalingDatacenterPower.txt")
  }
  

  //Used to dynamically create VMs
   private def createVm: Vm ={
    val config: Config = ConfigFactory.load("ScalingConfig.conf")
    val hostconfig = config.getConfig("HostConfig")
    val vmconfig = config.getConfig("VMConfig")
    createdVms = createdVms+1
     logger.debug("Creating new VM: "+createdVms)
    return new VmSimple(createdVms, hostconfig.getInt("HOST_MIPS"), vmconfig.getInt("VM_PES")).setRam(vmconfig.getInt("VM_RAM")).setBw(vmconfig.getInt("VM_BW")).setSize(vmconfig.getInt("VM_SIZE"))
   }
  //Setup for Horizontal Scaling
   private def createHorizontalScalingVm(vm: Vm): Unit ={
    val horizontalscaling: HorizontalVmScaling = new HorizontalVmScalingSimple
    horizontalscaling.setVmSupplier(() => createVm).setOverloadPredicate(isVmOverloaded)
     vm.setHorizontalScaling(horizontalscaling)
   }
  //Predicate to find is the VM is overloaded
   private def isVmOverloaded(vm: Vm): Boolean ={
     if (vm.getCpuPercentUtilization > 0.7 || vm.getHostRamUtilization > 0.5){
       logger.warn("VM: " + vm.getId + " is overloaded " + "CPU: " + vm.getCpuPercentUtilization + " RAM: " + vm.getHostRamUtilization)
       return true
     }
     else
       {
         return false
       }
   }

  private def createVerticalPeScaling: VerticalVmScaling  ={
    val scalingfactor = 0.1
    val verticalcpuscaling = new VerticalVmScalingSimple(classOf[Processor], scalingfactor)
    verticalcpuscaling.setResourceScaling(new ResourceScalingInstantaneous)
    verticalcpuscaling
  }
//creates VM that can scale
   private def createScalableVms(mips: Int, vms: Int, vm_pes: Int, vm_ram: Int, vm_bw: Int, vm_size: Int): util.Collection[Vm] = {
    val newVmList = new util.ArrayList[Vm]
    (1 to vms).map{_=>
      val vm: Vm = createVm
      createHorizontalScalingVm(vm)
      //vm.setPeVerticalScaling(createVerticalPeScaling)
      newVmList.add(vm)
    }
    return newVmList
   }
//creates the cloudlets
  private def createCloudletList(cloudlets: Int,cloudlet_length: Int, cloudlet_pes: Int,cloudlet_size: Int): Unit ={
   (1 to cloudlets).map{_=>
     cloudletList.add(createCloudlet(cloudlet_length,cloudlet_pes,cloudlet_size))

   }
  }
//creates a single cloudlet
   private def createCloudlet( cloudlet_length: Int, cloudlet_pes: Int, cloudlet_size: Int): Cloudlet ={
     createdCloudlets = createdCloudlets+1
    val cloudlet = new CloudletSimple(createdCloudlets, cloudlet_length, cloudlet_pes)
    cloudlet.setSizes(cloudlet_size)
    cloudlet.setUtilizationModelCpu(new UtilizationModelStochastic).setUtilizationModelRam(new UtilizationModelStochastic)
   }
//creates and submits new cloudlets as time goes on
   private def createNewCloudlets(info: EventInfo): Unit = {
     val config: Config = ConfigFactory.load("ScalingConfig.conf")
     val cloudletconfig = config.getConfig("CloudletConfig")
     val time: Long = info.getTime.toLong
    if(time % cloudletconfig.getInt("CREATION_INTERVAL") == 0 && time <=cloudletconfig.getInt("TIME_LIMIT")){
     val cloudletsNumber = cloudletconfig.getInt("CLOUDLETS_NEW")
     logger.debug("Creating " + cloudletconfig.getInt("CLOUDLETS") + " Cloudlets at time " + time)
     val newCloudletList = new util.ArrayList[Cloudlet](cloudletconfig.getInt("CLOUDLETS"))
     (1 to cloudletsNumber).map{_=>
       val cloudlet = new CloudletSimple(cloudletconfig.getInt("CLOUDLET_LENGTH"), cloudletconfig.getInt("CLOUDLET_PES"))
      cloudlet.setSizes(cloudletconfig.getInt("CLOUDLET_SIZE")).setSubmissionDelay((Random.nextInt().abs % 10) * 100)
      cloudlet.setUtilizationModelCpu(new UtilizationModelStochastic).setUtilizationModelRam(new UtilizationModelStochastic)
      newCloudletList.add(cloudlet)
      cloudletList.add(cloudlet)
     }
     broker.submitCloudletList(newCloudletList)
    }
   }
}
