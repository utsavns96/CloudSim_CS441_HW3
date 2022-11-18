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
 var cloudletid: Int = 0
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
  val treesimulation = new CloudSim
  //creating a new datacenter
  val datacenter0 = CreateObjects.createNetworkDatacenter(hostconfig.getInt("HOSTS"), hostconfig.getInt("HOST_PES"), hostconfig.getInt("HOST_MIPS"), hostconfig.getInt("HOST_RAM"), hostconfig.getInt("HOST_BW"), hostconfig.getInt("HOST_STORAGE"), treesimulation)
  //set datacenter0 costs
  datacenter0.getCharacteristics.setCostPerBw(dataconfig.getDouble("COSTPERBW")).setCostPerMem(dataconfig.getDouble("COSTPERMEM")).setCostPerSecond(dataconfig.getDouble("COSTPERSEC")).setCostPerStorage(dataconfig.getDouble("COSTBYSTORAGE"))
  logger.info("Created datacenter0")
  //set datacenter0 to use FirstFit policy for VM allocation
  datacenter0.setVmAllocationPolicy(new VmAllocationPolicyFirstFit)
  //create a tree network for datacenter0
  CreateObjects.createTreeNetwork(treesimulation, datacenter0)
  logger.info("Tree Network created for datacenter0")
  //create second datacenter
  val datacenter1 = CreateObjects.createNetworkDatacenter(hostconfig.getInt("HOSTS"), hostconfig.getInt("HOST_PES"), hostconfig.getInt("HOST_MIPS"), hostconfig.getInt("HOST_RAM"), hostconfig.getInt("HOST_BW"), hostconfig.getInt("HOST_STORAGE"), treesimulation)
  //set datacenter1 to use Round Robin policy for VM allocation
  datacenter1.setVmAllocationPolicy(new VmAllocationPolicyRoundRobin)
  //set datacenter1 costs
  datacenter1.getCharacteristics.setCostPerBw(data1config.getDouble("COSTPERBW")).setCostPerMem(data1config.getDouble("COSTPERMEM")).setCostPerSecond(data1config.getDouble("COSTPERSEC")).setCostPerStorage(data1config.getDouble("COSTBYSTORAGE"))
  logger.info("Created datacenter1")
  CreateObjects.createMeshNetwork(treesimulation, datacenter1)
  logger.info("Created Mesh network for datacenter1")
  val datacenterlist: List[NetworkDatacenter] = List(datacenter0,datacenter1)
  //creating a new broker to manage our VMs and Cloudlets
  val broker0 = new DatacenterBrokerSimple(treesimulation)
  //connecting the two datacenter
  CreateObjects.connectDatacenter(datacenterlist,broker0)
  //setting the delay for destruction of VMs
  broker0.setVmDestructionDelay(10.0)
  //treesimulation.terminateAt(1000)
  //creating our VMS
  val vmList = CreateObjects.createNetworkVms(hostconfig.getInt("HOST_MIPS"), vmconfig.getInt("VMS"), vmconfig.getInt("VM_PES"), vmconfig.getInt("VM_RAM"), vmconfig.getInt("VM_BW"), vmconfig.getInt("VM_SIZE"))
  logger.info("VMs created")
  //creating our new cloudlets
  val cloudletList = CreateObjects.createNetworkCloudlets(new UtilizationModelStochastic, cloudletconfig.getInt("CLOUDLETS"), cloudletconfig.getInt("CLOUDLET_LENGTH"), cloudletconfig.getInt("CLOUDLET_PES"), cloudletconfig.getInt("CLOUDLET_SIZE"))
  logger.info("Cloudlets created")
  //Assigning VMs to cloudlets
  //Distributes map and reduce tasks equally among VMs
  (0 to (cloudletList.size()-1)).map { i =>
   if(cloudletList.get(i).getLength == cloudletconfig.getInt("CLOUDLET_LENGTH")){
    if (vmList.get(i).getHost.getDatacenter == datacenter0) {
     logger.debug("Cloudlet: " + cloudletList.get(i).getId + " VM: " + i)
     cloudletList.get(i).setVm(vmList.get(i))
    }
   }
   else{
    logger.debug("Cloudlet: " + cloudletList.get(i).getId + " VM: " + i)
    cloudletList.get(i).setVm(vmList.get(i))
   }


  }
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
  treesimulation.start
  logger.info("-----------------------------------------Simulation finished-----------------------------------------")
  //getting our results
  val finishedCloudlets = broker0.getCloudletFinishedList
  //outputting our results as a table
  new CloudletsTableBuilder(finishedCloudlets).addColumn(new TextTableColumn("RAM Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfRam() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
    .addColumn(new TextTableColumn("CPU Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfCpu() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
    .build()
  //println("\n-------------------------------------------\n")
  val createdhosts0 = datacenter0.getHostList.asScala
  val filepower = new PrintWriter(new File("CloudproviderPower.txt"))
  createdhosts0.foreach(host => {
   val powerModel: PowerModelHost  = host.getPowerModel
   filepower.write("Host: "+host.getId+"\tTotal uptime:" + host.getTotalUpTime + " | Startup Time: " + powerModel.getTotalStartupTime + " | Startup Power: "+powerModel.getTotalStartupPower + " | Shutdown Time: "+powerModel.getTotalShutDownTime + " | Shutdown Power: "+powerModel.getTotalShutDownPower+"\n")
  })
  val createdhosts1 = datacenter0.getHostList.asScala
  createdhosts1.foreach(host => {
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
//Connects our datacenters
/* private def connectDatacenter(datacenterlist: List[Datacenter], broker: DatacenterBroker): Unit ={
  val networkTopology = new BriteNetworkTopology()
  networkTopology.addLink(datacenterlist(0), broker,1000,2 )
  networkTopology.addLink(datacenterlist(1), broker,1000,2 )
  networkTopology.addLink(datacenterlist(0),datacenterlist(1),1000,3)
 }*/
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
  //return cloudletList
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
 /**
  * Creates a new network datacenter based on our configs
  */

/* private def createNetworkDatacenter(host: Int, pes: Int, mpis: Int, ram: Int, bw: Int, storage: Int, sim: CloudSim): NetworkDatacenter = {
  val hostList = new util.ArrayList[NetworkHost](host)
  (1 to host).map { _ =>
   val host = createNetworkHost(new VmSchedulerTimeShared(), pes, mpis, ram, bw, storage)
   hostList.add(host)
  }
  val datacenter = new NetworkDatacenter(sim, hostList, new VmAllocationPolicyRoundRobin)
  datacenter
 }*/

 /**
  * Creates new network hosts based on our configs
  */
/* private def createNetworkHost(vmscheduler: VmScheduler, pes: Int, mips: Int, ram: Int, bw: Int, storage: Int): NetworkHost = {
  val peList = new util.ArrayList[Pe](pes)
  //Adding Processing Elements to our List
  (1 to pes).map { _ =>
   peList.add(new PeSimple(mips))
  }
   val host = new NetworkHost(ram, bw, storage, peList)
  val powerModel: PowerModelHost  = new PowerModelHostSimple(50,15)
  powerModel.setStartupDelay(0)
  powerModel.setShutDownDelay(10)
  powerModel.setStartupPower(40)
  powerModel.setShutDownPower(15)
  host.setPowerModel(powerModel)
  host.setIdleShutdownDeadline(2.0)
   host
 }*/
 /**
  * Creating a list of network VMs using our configs
  */
/* private def createNetworkVms(mips: Int, vms: Int, vm_pes: Int, vm_ram: Int, vm_bw: Int, vm_size: Int): java.util.List[NetworkVm] = {
  val vmList = new util.ArrayList[NetworkVm](vms)
  (0 to vms-1).map { i =>
   val vm = new NetworkVm(i, mips, vm_pes)
   vm.setRam(vm_ram).setBw(vm_bw).setSize(vm_size)
   vmList.add(vm)
  }
  vmList
 }*/
 /**
  * Creating a list of network Cloudlets from our configs
  */
/* private def createNetworkCloudlets(utilizationModel: UtilizationModel, cloudlets: Int, cloudlet_length: Int, cloudlet_pes: Int, cloudlet_size: Int): java.util.ArrayList[NetworkCloudlet] = {
   val cloudletList = new util.ArrayList[NetworkCloudlet](cloudlets)
   (1 to cloudlets).map { i =>
   val cloudlet = new NetworkCloudlet(cloudlet_length, cloudlet_pes)
   //val cloudlet = new CloudletSimple(cloudlet_length, cloudlet_pes)
   //val cloudlet = new CloudletSimple((Random.nextInt().abs % 10 + 1) * 10000, cloudlet_pes)
   cloudlet.setSizes(cloudlet_size)//.setSubmissionDelay((Random.nextInt().abs % 10) * 100)
   cloudlet.setUtilizationModelCpu(new UtilizationModelFull).setUtilizationModelRam(utilizationModel)
     .setFileSize(1000).setOutputSize(100)//.setVm(vm).setBroker(broker0)
   cloudletList.add(cloudlet)
    cloudlet.setId(cloudletid)
    logger.debug("Cloudlet created with ID = " + cloudlet.getId)
    cloudletid += 1
  }
  cloudletList
 }*/

 private def addExecutionTask(cloudlet: NetworkCloudlet): Unit ={
  val task = new CloudletExecutionTask(cloudlet.getTasks.size(), 5000)
  task.setMemory(256)
  cloudlet.addTask(task)
 }

 private def addRandomExecutionTask(cloudlet: NetworkCloudlet): Unit = {
  val task = new CloudletExecutionTask(cloudlet.getTasks.size(), ((Random.nextInt.abs%10)+1)*100)
  task.setMemory(256)
  cloudlet.addTask(task)
 }

 private def addSendTask(source: NetworkCloudlet, destination: NetworkCloudlet): Unit ={
  val task = new CloudletSendTask(source.getTasks.size())
  task.setMemory(256)//source.getMemory
  source.addTask(task)
  (1 to 100).map{_=>
    task.addPacket(destination, 1000)
  }
 }

 private def addRecvTask(cloudlet: NetworkCloudlet, source: NetworkCloudlet): Unit ={
  val task = new CloudletReceiveTask(cloudlet.getTasks.size(),source.getVm)
  task.setMemory(100)
  task.setExpectedPacketsToReceive(100)
  cloudlet.addTask(task)
 }

/* private def getSwitchIndex(host: NetworkHost, ports: Int): Int = {
  Math.round(host.getId % Int.MaxValue) / ports
 }*/
/*
 private def createTreeNetwork(sim: CloudSim, datacenter: NetworkDatacenter): Unit ={
  logger.debug("Creating Tree Network")
  val config: Config = ConfigFactory.load("CloudProvider.conf")
  val edgeconfig: Config = config.getConfig("TreeNetworkConfig.EdgeSwitchConfig")
  val aggregateconfig: Config = config.getConfig("TreeNetworkConfig.AggregateSwitchConfig")
  val rootconfig: Config = config.getConfig("TreeNetworkConfig.RootSwitchConfig")
  //adding edge switches
  val edgeswitches = edgeconfig.getInt("SWITCHES")
  val edgeSwitches: java.util.List[EdgeSwitch] = new util.ArrayList[EdgeSwitch]
  (1 to edgeswitches).map{e=>
    val edgeSwitch: EdgeSwitch = new EdgeSwitch(sim, datacenter)
    edgeSwitch.setPorts(edgeconfig.getInt("PORTS"))
    edgeSwitch.setUplinkBandwidth(edgeconfig.getInt("UP_BW"))
    edgeSwitch.setDownlinkBandwidth(edgeconfig.getInt("DOWN_BW"))
    edgeSwitch.setSwitchingDelay(edgeconfig.getInt("DELAY"))
    edgeSwitches.add(edgeSwitch)
    datacenter.addSwitch(edgeSwitch)
  }
  //adding aggregate switches
  val aggregateswitches = aggregateconfig.getInt("SWITCHES")
  val aggregateSwitches: java.util.List[AggregateSwitch] = new util.ArrayList[AggregateSwitch]
  (1 to aggregateswitches).map { e =>
    val aggregateSwitch: AggregateSwitch = new AggregateSwitch(sim, datacenter)
    aggregateSwitch.setPorts(aggregateconfig.getInt("PORTS"))
    aggregateSwitch.setUplinkBandwidth(aggregateconfig.getInt("UP_BW"))
    aggregateSwitch.setDownlinkBandwidth(aggregateconfig.getInt("DOWN_BW"))
    aggregateSwitch.setSwitchingDelay(aggregateconfig.getInt("DELAY"))
    aggregateSwitches.add(aggregateSwitch)
    datacenter.addSwitch(aggregateSwitch)
   }
  //adding root switch
    val rootSwitch: RootSwitch = new RootSwitch(sim, datacenter)
    rootSwitch.setPorts(rootconfig.getInt("PORTS"))
    rootSwitch.setUplinkBandwidth(rootconfig.getInt("UP_BW"))
    rootSwitch.setDownlinkBandwidth(rootconfig.getInt("DOWN_BW"))
    rootSwitch.setSwitchingDelay(rootconfig.getInt("DELAY"))
    datacenter.addSwitch(rootSwitch)

  //connecting the edge switches to the hosts
  val hostlist = datacenter.getHostList[NetworkHost]
  hostlist.forEach(e=>{
   val switchindex = getSwitchIndex(e,edgeconfig.getInt("PORTS"))
   edgeSwitches.get(switchindex).connectHost(e)
  })

  //connecting the aggregate switches to the root switches
  aggregateSwitches.asScala.foreach(aggregateswitch=>{
   aggregateswitch.getUplinkSwitches.add(rootSwitch)
   rootSwitch.getDownlinkSwitches.add(aggregateswitch)
  })

 //connecting edge switches to aggregate switches
  val sections = edgeSwitches.size()/aggregateSwitches.size()
  val pieces = edgeSwitches.size()/aggregateconfig.getInt("PORTS")
  val connected: java.util.List[EdgeSwitch] = new util.ArrayList[EdgeSwitch]
  logger.debug("Divide into: "+ pieces + " of size "+ sections)
  (1 to pieces).map(i=>{
   val subedge = edgeSwitches.subList(i-1,i*sections)
   logger.debug("subedge")
   (0 to (subedge.size()-1)).map(j=>{
    if(!connected.contains(subedge.get(j))){
     logger.debug(subedge.get(j).getId.toString)
     aggregateSwitches.get(i-1).getDownlinkSwitches.add(subedge.get(j))
     connected.add(subedge.get(j))
    }
   })
  })
  edgeSwitches.forEach({e=>
   logger.debug("Switch: "+e.getId+" Hosts: "+e.getHostList)
  })
  aggregateSwitches.forEach({e=>
   logger.debug("Switch: "+e.getId+" Downlink: ")
   e.getDownlinkSwitches.forEach({f=>
    logger.debug(f.getId.toString)
   })
   logger.debug(" Uplink:" + e.getUplinkSwitches)
  })
  }*/
//creates a mesh network
 /*private def createMeshNetwork(sim: CloudSim, datacenter: NetworkDatacenter): Unit ={
  logger.debug("Creating Mesh network")
  val config: Config = ConfigFactory.load("CloudProvider.conf")
  val edgeconfig: Config = config.getConfig("MeshNetworkConfig.EdgeSwitchConfig")
  val aggregateconfig: Config = config.getConfig("MeshNetworkConfig.AggregateSwitchConfig")
  val aggregatel2config: Config = config.getConfig("MeshNetworkConfig.AggregateSwitchLayer2Config")
  val rootconfig: Config = config.getConfig("MeshNetworkConfig.RootSwitchConfig")
  //adding edge switches
  val edgeswitches = edgeconfig.getInt("SWITCHES")
  val edgeSwitches: java.util.List[EdgeSwitch] = new util.ArrayList[EdgeSwitch]
  (1 to edgeswitches).map { e =>
   val edgeSwitch: EdgeSwitch = new EdgeSwitch(sim, datacenter)
   edgeSwitch.setPorts(edgeconfig.getInt("PORTS"))
   edgeSwitch.setUplinkBandwidth(edgeconfig.getInt("UP_BW"))
   edgeSwitch.setDownlinkBandwidth(edgeconfig.getInt("DOWN_BW"))
   edgeSwitch.setSwitchingDelay(edgeconfig.getInt("DELAY"))
   edgeSwitches.add(edgeSwitch)
   datacenter.addSwitch(edgeSwitch)
  }
  //adding aggregate switches
  val aggregateswitches = aggregateconfig.getInt("SWITCHES")
  val aggregateSwitches: java.util.List[AggregateSwitch] = new util.ArrayList[AggregateSwitch]
  (1 to aggregateswitches).map { e =>
   val aggregateSwitch: AggregateSwitch = new AggregateSwitch(sim, datacenter)
   aggregateSwitch.setPorts(aggregateconfig.getInt("PORTS"))
   aggregateSwitch.setUplinkBandwidth(aggregateconfig.getInt("UP_BW"))
   aggregateSwitch.setDownlinkBandwidth(aggregateconfig.getInt("DOWN_BW"))
   aggregateSwitch.setSwitchingDelay(aggregateconfig.getInt("DELAY"))
   aggregateSwitches.add(aggregateSwitch)
   datacenter.addSwitch(aggregateSwitch)
  }
  //Adding another layer of aggregate switches
  val aggregateswitchesL2 = aggregatel2config.getInt("SWITCHES")
  val aggregateSwitchesL2: java.util.List[AggregateSwitch] = new util.ArrayList[AggregateSwitch]
  (1 to aggregateswitchesL2).map { e =>
   val aggregateSwitchL2: AggregateSwitch = new AggregateSwitch(sim, datacenter)
   aggregateSwitchL2.setPorts(aggregatel2config.getInt("PORTS"))
   aggregateSwitchL2.setUplinkBandwidth(aggregatel2config.getInt("UP_BW"))
   aggregateSwitchL2.setDownlinkBandwidth(aggregatel2config.getInt("DOWN_BW"))
   aggregateSwitchL2.setSwitchingDelay(aggregatel2config.getInt("DELAY"))
   aggregateSwitchesL2.add(aggregateSwitchL2)
   datacenter.addSwitch(aggregateSwitchL2)
  }
  //adding root switch
  val rootSwitch: RootSwitch = new RootSwitch(sim, datacenter)
  rootSwitch.setPorts(rootconfig.getInt("PORTS"))
  rootSwitch.setUplinkBandwidth(rootconfig.getInt("UP_BW"))
  rootSwitch.setDownlinkBandwidth(rootconfig.getInt("DOWN_BW"))
  rootSwitch.setSwitchingDelay(rootconfig.getInt("DELAY"))
  datacenter.addSwitch(rootSwitch)
  //connecting all the edge switches to all the hosts
  val hostlist = datacenter.getHostList[NetworkHost]
  hostlist.forEach(e => {
   edgeSwitches.forEach(edge =>{
    edge.connectHost(e)
   })
  })
  //Adding root switches to Layer 2 Aggregate Switches
  aggregateSwitchesL2.forEach(aggregateL2=>{
   aggregateL2.getUplinkSwitches.add(rootSwitch)
  })
  //connecting the aggregate switches in layer 2 to those in layer 1
  val sections = aggregateSwitches.size() / aggregateSwitchesL2.size()
  val pieces = aggregateSwitches.size() / aggregatel2config.getInt("PORTS")
  val connected: java.util.List[AggregateSwitch] = new util.ArrayList[AggregateSwitch]
  (1 to pieces).map(i => {
   val subedge = aggregateSwitches.subList(i - 1, i * sections)
   println("subedge")
   (0 to (subedge.size() - 1)).map(j => {
    if (!connected.contains(subedge.get(j))) {
     println(subedge.get(j).getId)
     aggregateSwitchesL2.get(i - 1).getDownlinkSwitches.add(subedge.get(j))
     subedge.get(j).getUplinkSwitches.add(aggregateSwitchesL2.get(i-1))
     connected.add(subedge.get(j))
    }
   })
  })
  //connect aggregate switches in layer 1 to all the edge switches
  aggregateSwitches.forEach(aggregate=>{
   edgeSwitches.forEach(edge=>{
    aggregate.getDownlinkSwitches.add(edge)
   })
  })
  //Now, we connect all aggregate switches in layer 2 to each other
  (0 to (aggregateSwitchesL2.size()-1)).map(i=>{
   val next = if i+1<aggregateSwitchesL2.size() then i+1 else 0
   val prev = if i-1>0 then i-1 else 0
   aggregateSwitchesL2.get(i).getUplinkSwitches.add(aggregateSwitchesL2.get(next))
   aggregateSwitchesL2.get(i).getDownlinkSwitches.add(aggregateSwitchesL2.get(prev))
  })
  edgeSwitches.forEach({ e =>
   logger.debug("Edge Switch: " + e.getId + " Hosts: " + e.getHostList)
  })
  aggregateSwitches.forEach({ e =>
   logger.debug("Aggregate L1 Switch: " + e.getId + " Downlink: ")
   e.getDownlinkSwitches.forEach({ f =>
    println(f.getId)
   })
   logger.debug(" Uplink:" + e.getUplinkSwitches)
  })
  aggregateSwitchesL2.forEach({ e =>
   logger.debug("Aggregate L2 Switch: " + e.getId +" Name: "+ e + " Downlink: ")
   e.getDownlinkSwitches.forEach({ f =>
    logger.debug(f.getId.toString)
   })
   logger.debug(" Uplink:" + e.getUplinkSwitches)
  })
  //Thread.sleep(10000000)
 }*/

}
