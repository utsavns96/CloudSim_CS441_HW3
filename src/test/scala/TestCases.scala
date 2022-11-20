import CreateDatacenter.CreateObjects
import CreateDatacenter.CreateObjects.createHost
import com.typesafe.config.{Config, ConfigFactory}
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.datacenters.Datacenter
import org.cloudbus.cloudsim.hosts.Host
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic
import org.cloudbus.cloudsim.vms.Vm
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers.*

import java.io.*

class TestCases extends AnyFunSuite {
  test("Unit Test for VmAllocation.conf being present"){
    val file = File("src/main/resources/VmAllocation.conf")
    assert(file.exists)
  }
  test("Unit Test for VmUtilAndSchedule.conf being present"){
    val file = File("src/main/resources/VmUtilAndSchedule.conf")
    assert(file.exists)
  }
  test("Unit test for CloudProvider.conf being present"){
    val file = File("src/main/resources/CloudProvider.conf")
    assert(file.exists)
  }
  test("Unit Test for ScalingConfig.conf being present"){
    val file = File("src/main/resources/ScalingConfig.conf")
    assert(file.exists)
  }
  test("Unit test for all configs of IaasPaasSaas"){
    val fileiaas1 = File("src/main/resources/IaasProvider.conf")
    val fileiaas2 = File("src/main/resources/IaasUser.conf")
    val filepaas1 = File("src/main/resources/PaasProvider.conf")
    val filepaas2 = File("src/main/resources/PaasUser.conf")
    val filesaas1 = File("src/main/resources/SaasProvider.conf")
    val filesaas2 = File("src/main/resources/SaasUser.conf")
    assert(fileiaas1.exists && fileiaas2.exists() && filepaas1.exists() && filepaas2.exists() && filesaas1.exists() && filesaas2.exists())
  }
  test("Unit Test for VM instances being created"){
    val config: Config = ConfigFactory.load("VmAllocation.conf")
    val vmconfig = config.getConfig("VMConfig")
    val hostconfig = config.getConfig("HostConfig")
    val vmList = CreateObjects.createVms(hostconfig.getInt("HOST_MIPS"), vmconfig.getInt("VMS"), vmconfig.getInt("VM_PES"), vmconfig.getInt("VM_RAM"), vmconfig.getInt("VM_BW"), vmconfig.getInt("VM_SIZE"))
    vmList shouldBe a [java.util.ArrayList[Vm]]
  }
  test("Unit Test for Datacenter being created"){
    val config: Config = ConfigFactory.load("VmAllocation.conf")
    val dataconfig = config.getConfig("DatacenterConfig")
    val hostconfig = config.getConfig("HostConfig")
    val simulation = new CloudSim
    val datacenter = CreateObjects.createDatacenter(new VmSchedulerTimeShared, hostconfig.getInt("HOSTS"), hostconfig.getInt("HOST_PES"), hostconfig.getInt("HOST_MIPS"), hostconfig.getInt("HOST_RAM"), hostconfig.getInt("HOST_BW"), hostconfig.getInt("HOST_STORAGE"), simulation, dataconfig)
    datacenter shouldBe a [Datacenter]
  }
  test("Unit test for for hosts being created"){
    val config: Config = ConfigFactory.load("VmAllocation.conf")
    val dataconfig = config.getConfig("DatacenterConfig")
    val hostconfig = config.getConfig("HostConfig")
    val host = createHost(new VmSchedulerTimeShared(), hostconfig.getInt("HOST_PES"), hostconfig.getInt("HOST_MIPS"), hostconfig.getInt("HOST_RAM"), hostconfig.getInt("HOST_BW"), hostconfig.getInt("HOST_STORAGE"), dataconfig)
    host shouldBe a [Host]
  }
  test("Unit test for all VMs being allocated"){
    val config: Config = ConfigFactory.load("VmAllocation.conf")
    val hostconfig = config.getConfig("HostConfig")
    val vmconfig = config.getConfig("VMConfig")
    val cloudletconfig = config.getConfig("CloudletConfig")
    val dataconfig = config.getConfig("DatacenterConfig")
    //starting a new simulation
    val simulation = new CloudSim
    //creating a new datacenter
    val datacenter0 = CreateObjects.createDatacenter(new VmSchedulerTimeShared, hostconfig.getInt("HOSTS"), hostconfig.getInt("HOST_PES"), hostconfig.getInt("HOST_MIPS"), hostconfig.getInt("HOST_RAM"), hostconfig.getInt("HOST_BW"), hostconfig.getInt("HOST_STORAGE"), simulation, dataconfig)
    //creating a new broker to manage our VMs and Cloudlets
    val broker = new DatacenterBrokerSimple(simulation)
    //creating our VMS
    val vmList = CreateObjects.createVms(hostconfig.getInt("HOST_MIPS"), vmconfig.getInt("VMS"), vmconfig.getInt("VM_PES"), vmconfig.getInt("VM_RAM"), vmconfig.getInt("VM_BW"), vmconfig.getInt("VM_SIZE"))
    val cloudletList = CreateObjects.createCloudlets(new UtilizationModelDynamic(0.5), cloudletconfig.getInt("CLOUDLETS"), cloudletconfig.getInt("CLOUDLET_LENGTH"), cloudletconfig.getInt("CLOUDLET_PES"), cloudletconfig.getInt("CLOUDLET_SIZE"))
    broker.submitVmList(vmList)
    broker.submitCloudletList(cloudletList)
    simulation.start
    broker.getVmCreatedList.size() shouldEqual vmList.size()
  }
  test("Unit test for all cloudlets finishing execution"){
    val config: Config = ConfigFactory.load("VmAllocation.conf")
    val hostconfig = config.getConfig("HostConfig")
    val vmconfig = config.getConfig("VMConfig")
    val cloudletconfig = config.getConfig("CloudletConfig")
    val dataconfig = config.getConfig("DatacenterConfig")
    //starting a new simulation
    val simulation = new CloudSim
    //creating a new datacenter
    val datacenter0 = CreateObjects.createDatacenter(new VmSchedulerTimeShared, hostconfig.getInt("HOSTS"), hostconfig.getInt("HOST_PES"), hostconfig.getInt("HOST_MIPS"), hostconfig.getInt("HOST_RAM"), hostconfig.getInt("HOST_BW"), hostconfig.getInt("HOST_STORAGE"), simulation, dataconfig)
    //creating a new broker to manage our VMs and Cloudlets
    val broker = new DatacenterBrokerSimple(simulation)
    //creating our VMS
    val vmList = CreateObjects.createVms(hostconfig.getInt("HOST_MIPS"), vmconfig.getInt("VMS"), vmconfig.getInt("VM_PES"), vmconfig.getInt("VM_RAM"), vmconfig.getInt("VM_BW"), vmconfig.getInt("VM_SIZE"))
    val cloudletList = CreateObjects.createCloudlets(new UtilizationModelDynamic(0.5), cloudletconfig.getInt("CLOUDLETS"), cloudletconfig.getInt("CLOUDLET_LENGTH"), cloudletconfig.getInt("CLOUDLET_PES"), cloudletconfig.getInt("CLOUDLET_SIZE"))
    broker.submitVmList(vmList)
    broker.submitCloudletList(cloudletList)
    simulation.start
    val finishedCloudlets = broker.getCloudletFinishedList
    finishedCloudlets.size() shouldEqual cloudletList.size()
  }
}
