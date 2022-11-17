import com.typesafe.config.{Config, ConfigFactory}
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicyRoundRobin
import org.cloudbus.cloudsim.brokers.{DatacenterBroker, DatacenterBrokerSimple}
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletSimple}
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.datacenters.DatacenterSimple
import org.cloudbus.cloudsim.datacenters.network.NetworkDatacenter
import org.cloudbus.cloudsim.hosts.{Host, HostSimple}
import org.cloudbus.cloudsim.resources.{Pe, PeSimple, Processor}
import org.cloudbus.cloudsim.schedulers.vm.{VmScheduler, VmSchedulerTimeShared}
import org.cloudbus.cloudsim.utilizationmodels.{UtilizationModel, UtilizationModelFull, UtilizationModelStochastic}
import org.cloudbus.cloudsim.vms.{Vm, VmCost, VmSimple}
import org.cloudsimplus.autoscaling.resources.ResourceScalingInstantaneous
import org.cloudsimplus.autoscaling.{HorizontalVmScaling, HorizontalVmScalingSimple, VerticalVmScaling, VerticalVmScalingSimple}
import org.cloudsimplus.builders.tables.{CloudletsTableBuilder, TextTableColumn}
import org.cloudsimplus.listeners.EventInfo

import scala.jdk.CollectionConverters.*
import java.util
import java.util.function.Supplier
import scala.language.postfixOps
import scala.util.Random

object ScalingDatacenter {
   private val scheduling_interval = 5
   private val cloudletcreation_interval = scheduling_interval*2
   private val hostList = util.ArrayList[Host]
   private val vmList = util.ArrayList[Vm]
   private val cloudletList = util.ArrayList[Cloudlet]
   private var createdVms: Int = 0
   private var createdCloudlets: Int =0
    private var broker: DatacenterBroker = _
  def runScaling: Unit ={
    val config: Config = ConfigFactory.load("ScalingConfig.conf")
    val hostconfig = config.getConfig("HostConfig")
    val vmconfig = config.getConfig("VMConfig")
    val cloudletconfig = config.getConfig("CloudletConfig")
    val scalingsimulation = new CloudSim
    val datacenter = createDatacenter(hostconfig.getInt("HOSTS"), hostconfig.getInt("HOST_PES"), hostconfig.getInt("HOST_MIPS"), hostconfig.getInt("HOST_RAM"), hostconfig.getInt("HOST_BW"), hostconfig.getInt("HOST_STORAGE"), scalingsimulation)
    datacenter.setSchedulingInterval(1)
    broker = new DatacenterBrokerSimple(scalingsimulation)
    scalingsimulation.addOnClockTickListener(createNewCloudlets)
    vmList.addAll(createScalableVms(hostconfig.getInt("HOST_MIPS"), vmconfig.getInt("VMS"), vmconfig.getInt("VM_PES"), vmconfig.getInt("VM_RAM"), vmconfig.getInt("VM_BW"), vmconfig.getInt("VM_SIZE")))
    createCloudletList(cloudletconfig.getInt("CLOUDLETS"), cloudletconfig.getInt("CLOUDLET_LENGTH"), cloudletconfig.getInt("CLOUDLET_PES"), cloudletconfig.getInt("CLOUDLET_SIZE"))
    //createNewCloudlets(cloudletconfig.getInt("CLOUDLETS"), cloudletconfig.getInt("CLOUDLET_LENGTH"), cloudletconfig.getInt("CLOUDLET_PES"), cloudletconfig.getInt("CLOUDLET_SIZE"))
    broker.submitVmList(vmList)
    broker.submitCloudletList(cloudletList)
    scalingsimulation.start
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
    createdvms1.foreach(e => {
      val cost = new VmCost(e)
      processingtotalcost += cost.getProcessingCost
      memorycost += cost.getMemoryCost
      storagecost += cost.getStorageCost
      bwtotalcost += cost.getBwCost
      totalcost += cost.getTotalCost
      println(cost)
    })
  }

  //Used to dynamically create VMs
   private def createVm: Vm ={
    val config: Config = ConfigFactory.load("ScalingConfig.conf")
    val hostconfig = config.getConfig("HostConfig")
    val vmconfig = config.getConfig("VMConfig")
    createdVms = createdVms+1
     println("Creating new VM: "+createdVms)
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
       println("VM: " + vm.getId + " is overloaded " + "CPU: " + vm.getCpuPercentUtilization + " RAM: " + vm.getHostRamUtilization)
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

  private def createCloudletList(cloudlets: Int,cloudlet_length: Int, cloudlet_pes: Int,cloudlet_size: Int): Unit ={
   (1 to cloudlets).map{_=>
     cloudletList.add(createCloudlet(cloudlet_length,cloudlet_pes,cloudlet_size))

   }
  }
   private def createCloudlet( cloudlet_length: Int, cloudlet_pes: Int, cloudlet_size: Int): Cloudlet ={
     createdCloudlets = createdCloudlets+1
    val cloudlet = new CloudletSimple(createdCloudlets, cloudlet_length, cloudlet_pes)
    cloudlet.setSizes(cloudlet_size) //.setSubmissionDelay((Random.nextInt().abs % 10) * 100)
    cloudlet.setUtilizationModelCpu(new UtilizationModelStochastic).setUtilizationModelRam(new UtilizationModelStochastic)
   }

   private def createNewCloudlets(info: EventInfo): Unit = {
     val config: Config = ConfigFactory.load("ScalingConfig.conf")
     val cloudletconfig = config.getConfig("CloudletConfig")
     val time: Long = info.getTime.toLong
    if(time % cloudletcreation_interval == 0 && time <=50){
     val cloudletsNumber = cloudletconfig.getInt("CLOUDLETS_NEW")
     println("Creating " + cloudletconfig.getInt("CLOUDLETS") + " Cloudlets at time " + time)
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

  private def createDatacenter(host: Int, pes: Int, mpis: Int, ram: Int, bw: Int, storage: Int, sim: CloudSim): DatacenterSimple = {
    val config: Config = ConfigFactory.load("ScalingConfig.conf")
    val dataconfig: Config = config.getConfig("DatacenterConfig")
    val hostList = new util.ArrayList[Host](host)
    (1 to host).map { _ =>
      val host = createHost(new VmSchedulerTimeShared(), pes, mpis, ram, bw, storage)
      hostList.add(host)
    }
    val datacenter = new DatacenterSimple(sim, hostList, new VmAllocationPolicyRoundRobin)
    //datacenter.getCharacteristics.setCostPerBw(0.02).setCostPerMem(0.008).setCostPerSecond(0.02).setCostPerStorage(0.0002)
    //createTreeNetwork(sim, datacenter)
    datacenter.getCharacteristics.setCostPerBw(dataconfig.getDouble("COSTPERBW")).setCostPerMem(dataconfig.getDouble("COSTPERMEM")).setCostPerSecond(dataconfig.getDouble("COSTPERSEC")).setCostPerStorage(dataconfig.getDouble("COSTBYSTORAGE"))
    datacenter
  }

  private def createHost(vmscheduler: VmScheduler, pes: Int, mips: Int, ram: Int, bw: Int, storage: Int) = {
    val peList = new util.ArrayList[Pe](pes)
    //List of Host's CPUs (Processing Elements, PEs)
    //Uses a PeProvisionerSimple by default to provision PEs for VMs
    (1 to pes).map { _ =>
      peList.add(new PeSimple(mips))
    }
    new HostSimple(ram, bw, storage, peList).setVmScheduler(vmscheduler.getClass().getDeclaredConstructor().newInstance())
  }
}
