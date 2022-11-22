import HelperUtils.CreateLogger
import CreateDatacenter.CreateObjects
import com.typesafe.config.{Config, ConfigFactory}
import org.cloudbus.cloudsim.allocationpolicies.{VmAllocationPolicyFirstFit, VmAllocationPolicyRoundRobin}
import org.cloudbus.cloudsim.brokers.{DatacenterBroker, DatacenterBrokerSimple}
import org.cloudbus.cloudsim.cloudlets.network.{CloudletExecutionTask, CloudletReceiveTask, CloudletSendTask, NetworkCloudlet}
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletSimple}
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.datacenters.{Datacenter, DatacenterSimple}
import org.cloudbus.cloudsim.datacenters.network.NetworkDatacenter
import org.cloudbus.cloudsim.distributions.{ContinuousDistribution, UniformDistr}
import org.cloudbus.cloudsim.hosts.network.NetworkHost
import org.cloudbus.cloudsim.network.switches.{AggregateSwitch, EdgeSwitch, RootSwitch}
import org.cloudbus.cloudsim.network.topologies.{BriteNetworkTopology, NetworkTopology}
import org.cloudbus.cloudsim.hosts.{Host, HostSimple}
import org.cloudbus.cloudsim.power.models.{PowerModelHost, PowerModelHostSimple}
import org.cloudbus.cloudsim.resources.{Pe, PeSimple}
import org.cloudbus.cloudsim.schedulers.vm.{VmScheduler, VmSchedulerTimeShared}
import org.cloudbus.cloudsim.utilizationmodels.{UtilizationModel, UtilizationModelDynamic, UtilizationModelFull, UtilizationModelStochastic}
import org.cloudbus.cloudsim.vms.{Vm, VmCost, VmSimple}
import org.cloudbus.cloudsim.vms.network.NetworkVm
import org.cloudsimplus.builders.tables.{CloudletsTableBuilder, CsvTable, TextTableColumn}
import org.cloudbus.cloudsim.network.topologies.NetworkTopology

import scala.jdk.CollectionConverters.*
import java.util
import scala.language.postfixOps
import scala.util.Random
import java.io._


object CloudProvider {

 val logger = CreateLogger(classOf[CloudProvider.type])
 def runCloud: Unit ={
  logger.info("*****-----------------------------------------Starting Cloudprovider Simulation-----------------------------------------*****")
  val config: Config = ConfigFactory.load("CloudProvider.conf")
  val mainconfig = config.getConfig("CloudProviderConfig")
  val hostconfig = config.getConfig("HostConfig")
  val vmconfig = config.getConfig("VMConfig")
  val cloudletconfig = config.getConfig("CloudletConfig")
  val dataconfig: Config = config.getConfig("Datacenter0Config")
  val data1config: Config = config.getConfig("Datacenter1Config")
  val cloudprovidersim = new CloudSim
  //creating a new datacenter
  val datacenter0 = CreateObjects.createNetworkDatacenter(hostconfig.getInt("HOSTS"), hostconfig.getInt("HOST_PES"), hostconfig.getInt("HOST_MIPS"), hostconfig.getInt("HOST_RAM"), hostconfig.getInt("HOST_BW"), hostconfig.getInt("HOST_STORAGE"), cloudprovidersim,dataconfig)
  //set datacenter0 costs
  datacenter0.getCharacteristics.setCostPerBw(dataconfig.getDouble("COSTPERBW")).setCostPerMem(dataconfig.getDouble("COSTPERMEM")).setCostPerSecond(dataconfig.getDouble("COSTPERSEC")).setCostPerStorage(dataconfig.getDouble("COSTBYSTORAGE"))
  logger.info("Created datacenter0")
  //set datacenter0 to use FirstFit policy for VM allocation
  datacenter0.setVmAllocationPolicy(new VmAllocationPolicyFirstFit)
  //set power model for hosts
  //CreateObjects.setPowerModel(datacenter0.getHostList,dataconfig)
  //create a tree network for datacenter0
  CreateObjects.createTreeNetwork(cloudprovidersim, datacenter0)
  logger.info("Tree Network created for datacenter0")
  //create second datacenter
  val datacenter1 = CreateObjects.createNetworkDatacenter(hostconfig.getInt("HOSTS"), hostconfig.getInt("HOST_PES"), hostconfig.getInt("HOST_MIPS"), hostconfig.getInt("HOST_RAM"), hostconfig.getInt("HOST_BW"), hostconfig.getInt("HOST_STORAGE"), cloudprovidersim,data1config)
  //set datacenter1 to use Round Robin policy for VM allocation
  datacenter1.setVmAllocationPolicy(new VmAllocationPolicyRoundRobin)
  //set datacenter1 costs
  datacenter1.getCharacteristics.setCostPerBw(data1config.getDouble("COSTPERBW")).setCostPerMem(data1config.getDouble("COSTPERMEM")).setCostPerSecond(data1config.getDouble("COSTPERSEC")).setCostPerStorage(data1config.getDouble("COSTBYSTORAGE"))
  //set power model for hosts
  //CreateObjects.setPowerModel(datacenter1.getHostList,data1config)
  logger.info("Created datacenter1")
  CreateObjects.createMeshNetwork(cloudprovidersim, datacenter1)
  logger.info("Created Mesh network for datacenter1")
  val datacenterlist: List[NetworkDatacenter] = List(datacenter0,datacenter1)
  //creating a new broker to manage our VMs and Cloudlets
  val broker0 = new DatacenterBrokerSimple(cloudprovidersim)
  //connecting the two datacenter
  CreateObjects.connectDatacenter(datacenterlist,broker0, mainconfig)
  //setting the delay for destruction of VMs
  broker0.setVmDestructionDelay(mainconfig.getInt("VMDESTRUCTION"))
  //cloudprovidersim.terminateAt(mainconfig.getInt("SIM_TERMINATION"))
  //creating our VMS
  val vmList = CreateObjects.createNetworkVms(hostconfig.getInt("HOST_MIPS"), vmconfig.getInt("VMS"), vmconfig.getInt("VM_PES"), vmconfig.getInt("VM_RAM"), vmconfig.getInt("VM_BW"), vmconfig.getInt("VM_SIZE"))
  logger.info("VMs created")
  //creating our new cloudlets
  val cloudletList = CreateObjects.createNetworkCloudlets(new UtilizationModelDynamic(cloudletconfig.getDouble("DYNAMIC_UTIL")), cloudletconfig.getInt("CLOUDLETS"), cloudletconfig.getInt("CLOUDLET_LENGTH"), cloudletconfig.getInt("CLOUDLET_PES"), cloudletconfig.getInt("CLOUDLET_SIZE"))
  logger.info("Cloudlets created")
  //Distributes jobs among the VMs
  cloudletAlloc(cloudletconfig.getInt("CLOUDLETS") / vmconfig.getInt("VMS"),cloudletList,vmList)
  //Creates Map/Reduce tasks
  createMapperReducer(cloudletList.subList(0,cloudletList.size()/2))
  //creates a diffusion task
  createDiffusionTask(cloudletList.subList((cloudletList.size()/2), cloudletList.size()))
  //submitting our VMs
  broker0.submitVmList(vmList)
  logger.info("VMs submitted")
  //submitting our cloudlets
  broker0.submitCloudletList(cloudletList)
  logger.info("Cloudlets submitted")
  //starting the first simulation
  logger.info("-----------------------------------------Simulation started-----------------------------------------")
  cloudprovidersim.start
  logger.info("-----------------------------------------Simulation finished-----------------------------------------")
  //getting our results
  val finishedCloudlets = broker0.getCloudletFinishedList
  //outputting our results as a table
  new CloudletsTableBuilder(finishedCloudlets).addColumn(new TextTableColumn("RAM Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfRam() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
    .addColumn(new TextTableColumn("CPU Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfCpu() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
    .build()
  //Getting the power data
  val filepower = new PrintWriter(new File("CloudproviderPower.txt"))
  datacenter0.getHostList.asScala.foreach(host => {
   val powerModel: PowerModelHost  = host.getPowerModel
   filepower.write("Host: "+host.getId+"\tTotal uptime:" + host.getTotalUpTime + " | Startup Time: " + powerModel.getTotalStartupTime + " | Startup Power: "+powerModel.getTotalStartupPower + " | Shutdown Time: "+powerModel.getTotalShutDownTime + " | Shutdown Power: "+powerModel.getTotalShutDownPower+"\n")
  })
  datacenter1.getHostList.asScala.foreach(host => {
   val powerModel: PowerModelHost = host.getPowerModel
   filepower.write("Host: " + host.getId + "\tTotal uptime:" + host.getTotalUpTime + " | Startup Time: " + powerModel.getTotalStartupTime + " | Startup Power: " + powerModel.getTotalStartupPower + " | Shutdown Time: " + powerModel.getTotalShutDownTime + " | Shutdown Power: " + powerModel.getTotalShutDownPower + "\n")
  })
  filepower.close
  logger.info("Power usage data is available in CloudproviderPower.txt")
  println("\n-------------------------------------------\n")
  var totalcost = 0.0
  var processingtotalcost = 0.0
  var memorycost = 0.0
  var storagecost = 0.0
  var bwtotalcost = 0.0
  val createdvms1 = broker0.getVmCreatedList.asScala
  val file = new PrintWriter(new File("CloudproviderCost.txt"))
  createdvms1.foreach(e => {
   val cost = new VmCost(e)
   processingtotalcost += cost.getProcessingCost
   memorycost += cost.getMemoryCost
   storagecost += cost.getStorageCost
   bwtotalcost += cost.getBwCost
   totalcost += cost.getTotalCost
   println(cost)
   file.write("Vm: " + cost.getVm.toString+ "\tExecution Secs:"+ cost.getVm.getTotalExecutionTime + "\tCPU Cost: "+cost.getProcessingCost.toString+ "$\tRAM Cost: "+ cost.getMemoryCost.toString +"$\tStorage Cost:"+ cost.getStorageCost.toString+"$\tBW Cost:"+ cost.getBwCost.toString+ "$\tTotal Cost: "+ cost.getTotalCost.toString +"$\n")
  })
  file.close()
  logger.info("Cost data is available in CloudproviderCost.txt")
  val csv: CsvTable = new CsvTable()
  csv.setPrintStream(new PrintStream(new File("CloudproviderStats.csv")))
  new CloudletsTableBuilder(finishedCloudlets, csv).addColumn(new TextTableColumn("RAM Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfRam() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
    .addColumn(new TextTableColumn("CPU Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfCpu() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
    .addColumn(new TextTableColumn("Datacenter"),(cloudlet: Cloudlet) => (cloudlet.getLastTriedDatacenter))
    .build()
  logger.info("Simulation data is available in CloudproviderStats.csv")
 }
//creates map/reduce tasks
 private def createMapperReducer(cloudletList: java.util.List[NetworkCloudlet]): Unit ={
  logger.debug("Creating map/reduce tasks")
  (0 to (cloudletList.size() - 1)).map { i =>
   logger.debug("Cloudlet: " + cloudletList.get(i).getId + " i: " + i)
   if (i % 2 == 0) {
    //Mapper Task
    addExecutionTask(cloudletList.get(i))
    val next = if i + 1 < cloudletList.size() then i + 1 else 0
    addSendTask(cloudletList.get(i), cloudletList.get(next))
    logger.debug("Cloudlet " + cloudletList.get(i) + " Sends to Cloudlet " + cloudletList.get(next))
   }
   else {
    //Reducer Task
    val prev = if i - 1 > 0 then i - 1 else 0
    addRecvTask(cloudletList.get(i), cloudletList.get(prev))
    addExecutionTask(cloudletList.get(i))
    logger.debug("Cloudlet " + cloudletList.get(i) + " Receives from Cloudlet " + cloudletList.get(prev))
   }
  }
 }

//maps cloudlets to vms
private def cloudletAlloc(clpervm: Int, cloudletList: java.util.ArrayList[NetworkCloudlet], vmList: java.util.List[NetworkVm]): Unit ={
 var vmincrement = 0
 //maps half the cloudlets using first fit
 (0 to ((cloudletList.size() / 2) - 1)).map(i => {
  if (i != 0) {
   if (i % clpervm == 0) {
    vmincrement += 1
   }
  }
  logger.debug("Cloudlet: " + cloudletList.get(i).getId + " VM: " + vmincrement)
  cloudletList.get(i).setVm(vmList.get(vmincrement))
 })
 vmincrement += 1
 val vmoldincrement = vmincrement
 //maps the other half using round robin
 ((cloudletList.size() / 2) to cloudletList.size() - 1).map(i => {
  if (vmincrement - vmList.size() == 0) {
   vmincrement = vmoldincrement
  }
  logger.debug("Cloudlet: " + cloudletList.get(i).getId + " VM: " + vmincrement)
  cloudletList.get(i).setVm(vmList.get(vmincrement))
  vmincrement += 1
 })
}


//creates a diffusion task
 private def createDiffusionTask(cloudletList: java.util.List[NetworkCloudlet]): Unit ={
  val rootchildren: java.util.List[NetworkCloudlet] = new util.ArrayList[NetworkCloudlet]
  logger.debug("Creating Diffusion task")
  //root node performs tasks first
  (1 to cloudletList.size()-1).map(_=>{
   addExecutionTask(cloudletList.get(0))
  })
  //root node send tasks to children
  (1 to cloudletList.size()-1).map(i=>{
   addSendTask(cloudletList.get(0),cloudletList.get(i))//root send to all other nodes
   rootchildren.add(cloudletList.get(i))
   logger.debug("Root "+ cloudletList.get(0).getId + " Sends to " + cloudletList.get(i).getId)
  })//diffuse to 10 child nodes
  (1 to rootchildren.size()).map(c=>{
   addRecvTask(cloudletList.get(0),cloudletList.get(c))//root receives from all other nodes
   addRecvTask(cloudletList.get(c),cloudletList.get(0))//nodes receive message from root
   addExecutionTask(cloudletList.get(c))//nodes perform some fixed execution
   addRandomExecutionTask(cloudletList.get(c))//nodes perform some random length execution
   addExecutionTask(cloudletList.get(c))//nodes perform some fixed execution
   addSendTask(cloudletList.get(c),cloudletList.get(0))//nodes send result back to root
   logger.debug("Node: " + cloudletList.get(c).getId+" send results to "+cloudletList.get(0).getId)
  })
  (1 to cloudletList.size() - 1).map(i => {
   addExecutionTask(cloudletList.get(0))
  }) //perform some fixed computation after receiving results from
 }
//creates an execution task of a fixed length
 private def addExecutionTask(cloudlet: NetworkCloudlet): Unit ={
  val config: Config = ConfigFactory.load("CloudProvider.conf")
  val mainconfig = config.getConfig("CloudProviderConfig")
  val task = new CloudletExecutionTask(cloudlet.getTasks.size(), mainconfig.getInt("TASK_EXECUTION_LENGTH"))
  task.setMemory(256)
  cloudlet.addTask(task)
 }
//Creates an execution task of random length
 private def addRandomExecutionTask(cloudlet: NetworkCloudlet): Unit = {
  val config: Config = ConfigFactory.load("CloudProvider.conf")
  val mainconfig = config.getConfig("CloudProviderConfig")
  val task = new CloudletExecutionTask(cloudlet.getTasks.size(), ((Random.nextInt.abs%10)+1)*100)
  task.setMemory(mainconfig.getInt("SEND_TASK_MEMORY"))
  cloudlet.addTask(task)
 }
//cerates a send task
 private def addSendTask(source: NetworkCloudlet, destination: NetworkCloudlet): Unit ={
  val config: Config = ConfigFactory.load("CloudProvider.conf")
  val mainconfig = config.getConfig("CloudProviderConfig")
  val task = new CloudletSendTask(source.getTasks.size())
  task.setMemory(mainconfig.getInt("SEND_TASK_MEMORY"))//source.getMemory
  source.addTask(task)
  (1 to mainconfig.getInt("EXPECTED_PACKETS")).map{_=>
    task.addPacket(destination, mainconfig.getInt("PACKET_SIZE"))
  }
 }
//creates a receive task
 private def addRecvTask(cloudlet: NetworkCloudlet, source: NetworkCloudlet): Unit ={
  val config: Config = ConfigFactory.load("CloudProvider.conf")
  val mainconfig = config.getConfig("CloudProviderConfig")
  val task = new CloudletReceiveTask(cloudlet.getTasks.size(),source.getVm)
  task.setMemory(mainconfig.getInt("RECV_TASK_MEMORY"))
  task.setExpectedPacketsToReceive(mainconfig.getInt("EXPECTED_PACKETS"))
  cloudlet.addTask(task)
 }

}
