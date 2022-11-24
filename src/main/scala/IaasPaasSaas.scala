import CreateDatacenter.CreateObjects
import HelperUtils.CreateLogger
import com.typesafe.config.{Config, ConfigFactory}
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple
import org.cloudbus.cloudsim.cloudlets.Cloudlet
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.datacenters.DatacenterSimple
import org.cloudbus.cloudsim.power.models.PowerModelHost
import org.cloudbus.cloudsim.schedulers.vm.{VmSchedulerSpaceShared, VmSchedulerTimeShared}
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelStochastic
import org.cloudbus.cloudsim.vms.VmCost
import org.cloudsimplus.builders.tables.{CloudletsTableBuilder, TextTableColumn}

import scala.jdk.CollectionConverters.*

object IaasPaasSaas {
  var datacenteriaas: DatacenterSimple = _
  var datacenterpaas: DatacenterSimple = _
  var datacentersaas: DatacenterSimple = _
  val logger = CreateLogger(classOf[IaasPaasSaas.type])
  def runSim: Unit ={
    logger.info("*****-----------------------------------------Starting IaasPaasSaas Simulation-----------------------------------------*****")
    //Defining our config files
    val iaasproviderconfig: Config = ConfigFactory.load("IaasProvider.conf")
    val iaasuserconfig: Config = ConfigFactory.load("IaasUser.conf")
    val paasproviderconfig: Config = ConfigFactory.load("PaasProvider.conf")
    val paasuserconfig: Config = ConfigFactory.load("PaasUser.conf")
    val saasproviderconfig: Config = ConfigFactory.load("SaasProvider.conf")
    val saasuserconfig: Config = ConfigFactory.load("SaasUser.conf")
    //defining each config that we use
    //Iaas
    val iaashostconfig = iaasproviderconfig.getConfig("HostConfig")
    val iaasuservmconfig = iaasuserconfig.getConfig("VMConfig")
    val iaasprovidervmconfig = iaasproviderconfig.getConfig("VMConfig")
    val iaascloudletconfig = iaasuserconfig.getConfig("CloudletConfig")
    val iaasdataconfig = iaasproviderconfig.getConfig("DatacenterConfig")
    //Paas
    val paashostconfig = paasproviderconfig.getConfig("HostConfig")
    val paasvmconfig = paasproviderconfig.getConfig("VMConfig")
    val paasuservmconfig = paasuserconfig.getConfig("VMConfig")
    val paasprovidercloudletconfig = paasproviderconfig.getConfig("CloudletConfig")
    val paascloudletconfig = paasuserconfig.getConfig("CloudletConfig")
    val paasdataconfig = paasproviderconfig.getConfig("DatacenterConfig")
    //Saas
    val saashostconfig = saasproviderconfig.getConfig("HostConfig")
    val saasvmconfig = saasproviderconfig.getConfig("VMConfig")
    val saasprovidercloudletconfig = saasproviderconfig.getConfig("CloudletConfig")
    val saascloudletconfig = saasuserconfig.getConfig("CloudletConfig")
    val saasdataconfig = saasproviderconfig.getConfig("DatacenterConfig")
    //Creating our simualation
    val gradsim = new CloudSim
    val broker = new DatacenterBrokerSimple(gradsim)
    /**
     * creating the Iaas Datacenter
     */
    if (iaasprovidervmconfig.getString("VM_SCHEDULER").equals("spaceshared")) {
      datacenteriaas = CreateObjects.createDatacenter(new VmSchedulerSpaceShared, iaashostconfig.getInt("HOSTS"), iaashostconfig.getInt("HOST_PES"), iaashostconfig.getInt("HOST_MIPS"), iaashostconfig.getInt("HOST_RAM"), iaashostconfig.getInt("HOST_BW"), iaashostconfig.getInt("HOST_STORAGE"), gradsim, iaasdataconfig)
    }
    else if (iaasprovidervmconfig.getString("VM_SCHEDULER").equals("timeshared")) {
      datacenteriaas = CreateObjects.createDatacenter(new VmSchedulerTimeShared(), iaashostconfig.getInt("HOSTS"), iaashostconfig.getInt("HOST_PES"), iaashostconfig.getInt("HOST_MIPS"), iaashostconfig.getInt("HOST_RAM"), iaashostconfig.getInt("HOST_BW"), iaashostconfig.getInt("HOST_STORAGE"), gradsim,iaasdataconfig)
    }
    else {
      //default
      datacenteriaas = CreateObjects.createDatacenter(new VmSchedulerSpaceShared, iaashostconfig.getInt("HOSTS"), iaashostconfig.getInt("HOST_PES"), iaashostconfig.getInt("HOST_MIPS"), iaashostconfig.getInt("HOST_RAM"), iaashostconfig.getInt("HOST_BW"), iaashostconfig.getInt("HOST_STORAGE"), gradsim,iaasdataconfig)
    }
    datacenteriaas.getCharacteristics.setCostPerBw(iaasdataconfig.getDouble("COSTPERBW")).setCostPerMem(iaasdataconfig.getDouble("COSTPERMEM")).setCostPerSecond(iaasdataconfig.getDouble("COSTPERSEC")).setCostPerStorage(iaasdataconfig.getDouble("COSTBYSTORAGE"))
    logger.debug("Iaas Datacenter Created")
    /**
     * creating the Paas Datacenter
     */
    if (paasvmconfig.getString("VM_SCHEDULER").equals("spaceshared")) {
      datacenterpaas = CreateObjects.createDatacenter(new VmSchedulerSpaceShared, paashostconfig.getInt("HOSTS"), paashostconfig.getInt("HOST_PES"), paashostconfig.getInt("HOST_MIPS"), paashostconfig.getInt("HOST_RAM"), paashostconfig.getInt("HOST_BW"), paashostconfig.getInt("HOST_STORAGE"), gradsim,paasdataconfig)
    }
    else if (iaasprovidervmconfig.getString("VM_SCHEDULER").equals("timeshared")) {
      datacenterpaas = CreateObjects.createDatacenter(new VmSchedulerTimeShared(), paashostconfig.getInt("HOSTS"), paashostconfig.getInt("HOST_PES"), paashostconfig.getInt("HOST_MIPS"), paashostconfig.getInt("HOST_RAM"), paashostconfig.getInt("HOST_BW"), paashostconfig.getInt("HOST_STORAGE"), gradsim,paasdataconfig)
    }
    else {
      //default
      datacenterpaas = CreateObjects.createDatacenter(new VmSchedulerSpaceShared, paashostconfig.getInt("HOSTS"), paashostconfig.getInt("HOST_PES"), paashostconfig.getInt("HOST_MIPS"), paashostconfig.getInt("HOST_RAM"), paashostconfig.getInt("HOST_BW"), paashostconfig.getInt("HOST_STORAGE"), gradsim,paasdataconfig)
    }
    datacenterpaas.getCharacteristics.setCostPerBw(paasdataconfig.getDouble("COSTPERBW")).setCostPerMem(paasdataconfig.getDouble("COSTPERMEM")).setCostPerSecond(paasdataconfig.getDouble("COSTPERSEC")).setCostPerStorage(paasdataconfig.getDouble("COSTBYSTORAGE"))
    logger.debug("Paas Datacenter Created")
    /**
     * creating the Saas Datacenter
     */
    if (saasvmconfig.getString("VM_SCHEDULER").equals("spaceshared")) {
      datacentersaas = CreateObjects.createDatacenter(new VmSchedulerSpaceShared, saashostconfig.getInt("HOSTS"), saashostconfig.getInt("HOST_PES"), saashostconfig.getInt("HOST_MIPS"), saashostconfig.getInt("HOST_RAM"), saashostconfig.getInt("HOST_BW"), saashostconfig.getInt("HOST_STORAGE"), gradsim,saasdataconfig)
    }
    else if (iaasprovidervmconfig.getString("VM_SCHEDULER").equals("timeshared")) {
      datacentersaas = CreateObjects.createDatacenter(new VmSchedulerTimeShared(), saashostconfig.getInt("HOSTS"), saashostconfig.getInt("HOST_PES"), saashostconfig.getInt("HOST_MIPS"), saashostconfig.getInt("HOST_RAM"), saashostconfig.getInt("HOST_BW"), saashostconfig.getInt("HOST_STORAGE"), gradsim,saasdataconfig)
    }
    else {
      //default
      datacentersaas = CreateObjects.createDatacenter(new VmSchedulerSpaceShared, saashostconfig.getInt("HOSTS"), saashostconfig.getInt("HOST_PES"), saashostconfig.getInt("HOST_MIPS"), saashostconfig.getInt("HOST_RAM"), saashostconfig.getInt("HOST_BW"), saashostconfig.getInt("HOST_STORAGE"), gradsim,saasdataconfig)
    }
    datacentersaas.getCharacteristics.setCostPerBw(saasdataconfig.getDouble("COSTPERBW")).setCostPerMem(saasdataconfig.getDouble("COSTPERMEM")).setCostPerSecond(saasdataconfig.getDouble("COSTPERSEC")).setCostPerStorage(saasdataconfig.getDouble("COSTBYSTORAGE"))
    logger.debug("Saas Datacenter Created")
    /**
     * creating the Vms for the Iaas Datacenter
     */
    val vmListIaas = CreateObjects.createVms(iaashostconfig.getInt("HOST_MIPS"),iaasuservmconfig.getInt("VMS"), iaasuservmconfig.getInt("VM_PES"), iaasuservmconfig.getInt("VM_RAM"), iaasuservmconfig.getInt("VM_BW"), iaasuservmconfig.getInt("VM_SIZE"))
    /**
     * creating the Vms for the Iaas Datacenter
     */
    val vmListPaas = CreateObjects.createVms(paashostconfig.getInt("HOST_MIPS"),paasuservmconfig.getInt("VMS"), paasvmconfig.getInt("VM_PES"), paasvmconfig.getInt("VM_RAM"), paasvmconfig.getInt("VM_BW"), paasvmconfig.getInt("VM_SIZE"))
    /**
     * creating the Vms for the Iaas Datacenter
     */
    val vmListSaas = CreateObjects.createVms(saashostconfig.getInt("HOST_MIPS"),saasvmconfig.getInt("VMS"), saasvmconfig.getInt("VM_PES"), saasvmconfig.getInt("VM_RAM"), saasvmconfig.getInt("VM_BW"), saasvmconfig.getInt("VM_SIZE"))
    vmListIaas.addAll(vmListPaas)
    vmListIaas.addAll(vmListSaas)
    broker.setVmDestructionDelay(iaasprovidervmconfig.getInt("VMDESTRUCTION"))
    broker.submitVmList(vmListIaas)
    /**
     * creating the Cloudlets for the Iaas Datacenter
     */
    val iaascloudletList = CreateObjects.createCloudlets(new UtilizationModelStochastic, iaascloudletconfig.getInt("CLOUDLETS"), iaascloudletconfig.getInt("CLOUDLET_LENGTH"), iaascloudletconfig.getInt("CLOUDLET_PES"), iaascloudletconfig.getInt("CLOUDLET_SIZE"))
    val paascloudletList = CreateObjects.createCloudlets(new UtilizationModelStochastic, paascloudletconfig.getInt("CLOUDLETS"), paascloudletconfig.getInt("CLOUDLET_LENGTH"), paasprovidercloudletconfig.getInt("CLOUDLET_PES"), paascloudletconfig.getInt("CLOUDLET_SIZE"))
    val saascloudletList = CreateObjects.createCloudlets(new UtilizationModelStochastic, saascloudletconfig.getInt("CLOUDLETS"), saascloudletconfig.getInt("CLOUDLET_LENGTH"), saasprovidercloudletconfig.getInt("CLOUDLET_PES"), saascloudletconfig.getInt("CLOUDLET_SIZE"))

    iaascloudletList.addAll(paascloudletList)
    iaascloudletList.addAll(saascloudletList)
    broker.submitCloudletList(iaascloudletList)
    gradsim.start()
    //gathering the results of our second simulation
    val finishedCloudlets1 = broker.getCloudletFinishedList.asScala
    //outputting the results as a table
    new CloudletsTableBuilder(finishedCloudlets1.asJava).addColumn(new TextTableColumn("RAM Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfRam() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
      .addColumn(new TextTableColumn("CPU Utilization", "Percentage"), (cloudlet: Cloudlet) => BigDecimal(cloudlet.getUtilizationOfCpu() * 100.0).setScale(2, BigDecimal.RoundingMode.HALF_UP))
      .addColumn(new TextTableColumn("Datacenter"),(cloudlet: Cloudlet) => (cloudlet.getLastTriedDatacenter))
      .build()
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
    println("Iaas Power details")
    datacenteriaas.getHostList.asScala.foreach(host => {
      val powerModel: PowerModelHost = host.getPowerModel
      println("Host: " + host.getId + "\tTotal uptime:" + host.getTotalUpTime + " | Startup Time: " + powerModel.getTotalStartupTime + " | Startup Power: " + powerModel.getTotalStartupPower + " | Shutdown Time: " + powerModel.getTotalShutDownTime + " | Shutdown Power: " + powerModel.getTotalShutDownPower + "\n")
    })
    println("Paas Power details")
    datacenterpaas.getHostList.asScala.foreach(host => {
      val powerModel: PowerModelHost = host.getPowerModel
      println("Host: " + host.getId + "\tTotal uptime:" + host.getTotalUpTime + " | Startup Time: " + powerModel.getTotalStartupTime + " | Startup Power: " + powerModel.getTotalStartupPower + " | Shutdown Time: " + powerModel.getTotalShutDownTime + " | Shutdown Power: " + powerModel.getTotalShutDownPower + "\n")
    })
    println("Saas Power details")
    datacentersaas.getHostList.asScala.foreach(host => {
      val powerModel: PowerModelHost = host.getPowerModel
      println("Host: " + host.getId + "\tTotal uptime:" + host.getTotalUpTime + " | Startup Time: " + powerModel.getTotalStartupTime + " | Startup Power: " + powerModel.getTotalStartupPower + " | Shutdown Time: " + powerModel.getTotalShutDownTime + " | Shutdown Power: " + powerModel.getTotalShutDownPower + "\n")
    })
    logger.info("Simulation finished")
  }
}
