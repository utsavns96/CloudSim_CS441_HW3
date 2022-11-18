package CreateDatacenter
import HelperUtils.CreateLogger
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletSimple}
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.datacenters.DatacenterSimple
import org.cloudbus.cloudsim.hosts.{Host, HostSimple}
import org.cloudbus.cloudsim.resources.{Pe, PeSimple}
import org.cloudbus.cloudsim.schedulers.vm.VmScheduler
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel
import org.cloudbus.cloudsim.vms.{Vm, VmSimple}

import scala.util.Random
import java.util

object CreateObjects {
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
}
