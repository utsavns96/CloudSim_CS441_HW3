package CreateDatacenter
import HelperUtils.CreateLogger
import com.typesafe.config.{Config, ConfigFactory}
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicyRoundRobin
import org.cloudbus.cloudsim.brokers.DatacenterBroker
import org.cloudbus.cloudsim.cloudlets.network.NetworkCloudlet
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletSimple}
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.datacenters.network.NetworkDatacenter
import org.cloudbus.cloudsim.datacenters.{Datacenter, DatacenterSimple}
import org.cloudbus.cloudsim.hosts.network.NetworkHost
import org.cloudbus.cloudsim.hosts.{Host, HostSimple}
import org.cloudbus.cloudsim.network.switches.{AggregateSwitch, EdgeSwitch, RootSwitch}
import org.cloudbus.cloudsim.network.topologies.BriteNetworkTopology
import org.cloudbus.cloudsim.power.models.{PowerModelHost, PowerModelHostSimple}
import org.cloudbus.cloudsim.resources.{Pe, PeSimple}
import org.cloudbus.cloudsim.schedulers.vm.{VmScheduler, VmSchedulerTimeShared}
import org.cloudbus.cloudsim.utilizationmodels.{UtilizationModel, UtilizationModelFull}
import org.cloudbus.cloudsim.vms.network.NetworkVm
import org.cloudbus.cloudsim.vms.{Vm, VmSimple}

import scala.util.Random
import java.util
import scala.language.postfixOps
import scala.jdk.CollectionConverters.*

object CreateObjects {
  var cloudletid: Int = 0
  val logger = CreateLogger(classOf[CreateObjects.type])

  /**
   *  Creates a new datacenter based on our configs
   */
  def createDatacenter(vmscheduler: VmScheduler, host: Int, pes: Int, mpis: Int, ram: Int, bw: Int, storage: Int, sim: CloudSim) = {
    val hostList = new util.ArrayList[Host](host)
    (1 to host).map { i =>
      val host = createHost(vmscheduler, pes, mpis, ram, bw, storage)
      hostList.add(host)
      logger.debug("Host "+i+" created")
    }
    new DatacenterSimple(sim, hostList)
  }

  /**
   * Creates a new hosts based on our configs
   */
  def createHost(vmscheduler: VmScheduler, pes: Int, mips: Int, ram: Int, bw: Int, storage: Int) = {
    val peList = new util.ArrayList[Pe](pes)
    //List of Host's CPUs (Processing Elements, PEs)
    //Uses a PeProvisionerSimple by default to provision PEs for VMs
    (1 to pes).map { _ =>
      peList.add(new PeSimple(mips))
    }
    new HostSimple(ram, bw, storage, peList).setVmScheduler(vmscheduler.getClass().getDeclaredConstructor().newInstance())
  }

  /**
   * Creates a new VMs based on our configs
   */
  def createVms(mips: Int, vms: Int, vm_pes: Int, vm_ram: Int, vm_bw: Int, vm_size: Int) = {
    val vmList = new util.ArrayList[Vm](vms)
    (1 to vms).map { _ =>
      val vm = new VmSimple(mips, vm_pes)
      vm.setRam(vm_ram).setBw(vm_bw).setSize(vm_size)
      vmList.add(vm)
    }
    vmList
  }

  /**
   * Creates a new Cloudlets based on our configs
   */
  def createCloudlets(utilizationModel: UtilizationModel, cloudlets: Int, cloudlet_length: Int, cloudlet_pes: Int, cloudlet_size: Int) = {
    val cloudletList = new util.ArrayList[Cloudlet](cloudlets)
    new Random()
    (1 to cloudlets).map { _ =>
      val cloudlet = new CloudletSimple((Random.nextInt().abs % 10 + 1) * 10000, cloudlet_pes, utilizationModel)
      cloudlet.setSizes(cloudlet_size).setSubmissionDelay((Random.nextInt().abs % 10) * 1000)
      cloudletList.add(cloudlet)
    }
    cloudletList
  }

   def connectDatacenter(datacenterlist: List[Datacenter], broker: DatacenterBroker): Unit = {
    val networkTopology = new BriteNetworkTopology()
    networkTopology.addLink(datacenterlist(0), broker, 1000, 2)
    networkTopology.addLink(datacenterlist(1), broker, 1000, 2)
    networkTopology.addLink(datacenterlist(0), datacenterlist(1), 1000, 3)
  }

   def createNetworkDatacenter(host: Int, pes: Int, mpis: Int, ram: Int, bw: Int, storage: Int, sim: CloudSim): NetworkDatacenter = {
    val hostList = new util.ArrayList[NetworkHost](host)
    (1 to host).map { _ =>
      val host = createNetworkHost(new VmSchedulerTimeShared(), pes, mpis, ram, bw, storage)
      hostList.add(host)
    }
    val datacenter = new NetworkDatacenter(sim, hostList, new VmAllocationPolicyRoundRobin)
    datacenter
  }

   def createNetworkHost(vmscheduler: VmScheduler, pes: Int, mips: Int, ram: Int, bw: Int, storage: Int): NetworkHost = {
    val peList = new util.ArrayList[Pe](pes)
    //Adding Processing Elements to our List
    (1 to pes).map { _ =>
      peList.add(new PeSimple(mips))
    }
    val host = new NetworkHost(ram, bw, storage, peList)
    val powerModel: PowerModelHost = new PowerModelHostSimple(50, 15)
    powerModel.setStartupDelay(0)
    powerModel.setShutDownDelay(10)
    powerModel.setStartupPower(40)
    powerModel.setShutDownPower(15)
    host.setPowerModel(powerModel)
    host.setIdleShutdownDeadline(2.0)
    host
  }

   def createNetworkVms(mips: Int, vms: Int, vm_pes: Int, vm_ram: Int, vm_bw: Int, vm_size: Int): java.util.List[NetworkVm] = {
    val vmList = new util.ArrayList[NetworkVm](vms)
    (0 to vms - 1).map { i =>
      val vm = new NetworkVm(i, mips, vm_pes)
      vm.setRam(vm_ram).setBw(vm_bw).setSize(vm_size)
      vmList.add(vm)
    }
    vmList
  }

   def createNetworkCloudlets(utilizationModel: UtilizationModel, cloudlets: Int, cloudlet_length: Int, cloudlet_pes: Int, cloudlet_size: Int): java.util.ArrayList[NetworkCloudlet] = {
    val cloudletList = new util.ArrayList[NetworkCloudlet](cloudlets)
    (1 to cloudlets).map { i =>
      val cloudlet = new NetworkCloudlet(cloudlet_length, cloudlet_pes)
      //val cloudlet = new CloudletSimple(cloudlet_length, cloudlet_pes)
      //val cloudlet = new CloudletSimple((Random.nextInt().abs % 10 + 1) * 10000, cloudlet_pes)
      cloudlet.setSizes(cloudlet_size) //.setSubmissionDelay((Random.nextInt().abs % 10) * 100)
      cloudlet.setUtilizationModelCpu(new UtilizationModelFull).setUtilizationModelRam(utilizationModel)
        .setFileSize(1000).setOutputSize(100) //.setVm(vm).setBroker(broker0)
      cloudletList.add(cloudlet)
      cloudlet.setId(cloudletid)
      logger.debug("Cloudlet created with ID = " + cloudlet.getId)
      cloudletid += 1
    }
    cloudletList
  }

   def getSwitchIndex(host: NetworkHost, ports: Int): Int = {
    Math.round(host.getId % Int.MaxValue) / ports
  }
   def createTreeNetwork(sim: CloudSim, datacenter: NetworkDatacenter): Unit = {
    logger.debug("Creating Tree Network")
    val config: Config = ConfigFactory.load("CloudProvider.conf")
    val edgeconfig: Config = config.getConfig("TreeNetworkConfig.EdgeSwitchConfig")
    val aggregateconfig: Config = config.getConfig("TreeNetworkConfig.AggregateSwitchConfig")
    val rootconfig: Config = config.getConfig("TreeNetworkConfig.RootSwitchConfig")
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
    //adding root switch
    val rootSwitch: RootSwitch = new RootSwitch(sim, datacenter)
    rootSwitch.setPorts(rootconfig.getInt("PORTS"))
    rootSwitch.setUplinkBandwidth(rootconfig.getInt("UP_BW"))
    rootSwitch.setDownlinkBandwidth(rootconfig.getInt("DOWN_BW"))
    rootSwitch.setSwitchingDelay(rootconfig.getInt("DELAY"))
    datacenter.addSwitch(rootSwitch)

    //connecting the edge switches to the hosts
    val hostlist = datacenter.getHostList[NetworkHost]
    hostlist.forEach(e => {
      val switchindex = getSwitchIndex(e, edgeconfig.getInt("PORTS"))
      edgeSwitches.get(switchindex).connectHost(e)
    })

    //connecting the aggregate switches to the root switches
    aggregateSwitches.asScala.foreach(aggregateswitch => {
      aggregateswitch.getUplinkSwitches.add(rootSwitch)
      rootSwitch.getDownlinkSwitches.add(aggregateswitch)
    })

    //connecting edge switches to aggregate switches
    val sections = edgeSwitches.size() / aggregateSwitches.size()
    val pieces = edgeSwitches.size() / aggregateconfig.getInt("PORTS")
    val connected: java.util.List[EdgeSwitch] = new util.ArrayList[EdgeSwitch]
    logger.debug("Divide into: " + pieces + " of size " + sections)
    (1 to pieces).map(i => {
      val subedge = edgeSwitches.subList(i - 1, i * sections)
      logger.debug("subedge")
      (0 to (subedge.size() - 1)).map(j => {
        if (!connected.contains(subedge.get(j))) {
          logger.debug(subedge.get(j).getId.toString)
          aggregateSwitches.get(i - 1).getDownlinkSwitches.add(subedge.get(j))
          connected.add(subedge.get(j))
        }
      })
    })
    edgeSwitches.forEach({ e =>
      logger.debug("Switch: " + e.getId + " Hosts: " + e.getHostList)
    })
    aggregateSwitches.forEach({ e =>
      logger.debug("Switch: " + e.getId + " Downlink: ")
      e.getDownlinkSwitches.forEach({ f =>
        logger.debug(f.getId.toString)
      })
      logger.debug(" Uplink:" + e.getUplinkSwitches)
    })
  }

  //creates a mesh network
   def createMeshNetwork(sim: CloudSim, datacenter: NetworkDatacenter): Unit = {
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
      edgeSwitches.forEach(edge => {
        edge.connectHost(e)
      })
    })
    //Adding root switches to Layer 2 Aggregate Switches
    aggregateSwitchesL2.forEach(aggregateL2 => {
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
          subedge.get(j).getUplinkSwitches.add(aggregateSwitchesL2.get(i - 1))
          connected.add(subedge.get(j))
        }
      })
    })
    //connect aggregate switches in layer 1 to all the edge switches
    aggregateSwitches.forEach(aggregate => {
      edgeSwitches.forEach(edge => {
        aggregate.getDownlinkSwitches.add(edge)
      })
    })
    //Now, we connect all aggregate switches in layer 2 to each other
    (0 to (aggregateSwitchesL2.size() - 1)).map(i => {
      val next = if i + 1 < aggregateSwitchesL2.size() then i + 1 else 0
      val prev = if i - 1 > 0 then i - 1 else 0
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
      logger.debug("Aggregate L2 Switch: " + e.getId + " Name: " + e + " Downlink: ")
      e.getDownlinkSwitches.forEach({ f =>
        logger.debug(f.getId.toString)
      })
      logger.debug(" Uplink:" + e.getUplinkSwitches)
    })
    //Thread.sleep(10000000)
  }
}
