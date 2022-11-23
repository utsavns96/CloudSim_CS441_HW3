# CS441_Fall2022_HW1
## Utsav Sharma
### UIN: 665894994
### NetID: usharm4@uic.edu

Repo for the Cloudsim Plus Homework-3 for CS411-Fall2022

---

## Running the project

1) Download the repo from git.
2) To compile the project, navigate to its directory and run `sbt clean compile` in the terminal
3) To run the project, run `sbt run` in the terminal and choose the simulation from the menu by entering the corresponding number and pressing `Enter`
4) TO run the test suite, run `sbt test` in the terminal window.

---

## Technical Design
This project is split into 5 different simulations that we will look at below. All programs use config files and create logs in the /log directory of the project.

### 1) RunJobs.scala
This program acts as our menu for running our 5 simulations.<br>
[1] runs the VMAllocation simulation <br>
[2] runs the VMUtilAndSchedule simulation <br>
[3] runs the Network simulation CloudProvider with multiple datacenters <br>
[4] runs the ScalingDatacenter simulation <br>
[5] runs the Grad simulation for Iaas, Paas and Saas.

### 2) VMAllocation.scala
This simulation runs two Cloudsim simulations that compare the effects of different VM allocation policies.<br>
For the first simulation, we first load our configs and create a new simulation. Then, we create a new datacenter with hosts and processing elements.
The datacenter is created through `CreateObjects.createDatacenter` in the `CreateDatacenter` package where first we create the number of hosts that are specified in the config files.
The hosts are created using createHost, where the PEs are generated from 1 to the number of PEs from the config files, and the power model is set for each host. The hosts are returned to the createDatacenter, where they are added to a list, and finally the list of hosts is added to the datacenter.<br>
Then, a broker is created for this simulation, followed by creating the list of VMs that we need to run on these hosts. The list of VMs is generated through `CreateObjects.createVms`, where we pass the VM configs from the config file.
`createVM` returns a list of VMs to our program after creating a new VM, setting the parameters such as RAM, Bandwidth, etc and adding them to a list. The VM list is copied to a second val to be used later for our second simulation.<br>
The cloudlets are created after this, where we pass the utilization model and the cloudlet parameters from the config file, and get a list of cloudlets in return after they have been created in `CreateObjects.createCloudlets`.
We set the VM allocation policy to `VmAllocationPolicyFirstFit` and then submit our VM list and cloudlet list to the broker.<br>
The simulation is then executed and the results of the simualtion are printed to the terminal. This ends our first simulation for this program.<br>
For the second simulation, we take the same steps as above, but instead of First Fit, we use `VmAllocationPolicyRoundRobin`. The simulation is then run and the results are printed as stdout. This end the second simulation.

### 3) VMUtilAndSchedule.scala
This program runs two Cloudsim simulations that compares different VM Scheduler and Utilization policies. To do this, we first run a simulation just as described above. However, while creating our datacenters we specify the first datacenter to use VmSchedulerSpaceShared, and the second datacenter to use VmSchedulerTimeShared. (This is controlled through the config file and can be flipped around too.)<br>
The cloudlets in the first simulation use UtilizationModelStochastic, while the second simulation use UtilizationModelFull.<br>
While creating the datacenters, we also specify the costs the datacenter has, which we then use at the end of the simulations to collect the cost of running our particular setup.

### 4) CloudProvider.scala
This program implements network topologies in multiple datacenters. To do this, we first start with loading our configurations, after which we create our simulation. THen, we create our first network datacenter using `CreateObjects.createNetworkDatacenter`. We pass the host configurations, the simulation and the datacenter configurations to this.
In createNetworkDatacenter, we create a list of hosts that we populate using createNetworkHost where the processing elements are created and passed to NetworkHost to create new hosts, after which a power model is added to the hosts.
Once this is done, we configure the costs of the datacenter and specify it to use VmAllocationPolicyFirstFit.<br>
Now, we create a Tree network topology for this datacenter using the configurations of the various switches from the CloudProvider.conf file. To create this topology, we generate our edge switches, aggregate switches and root switch and set their respective parameters. Then, the edge switches are connected to the hosts first, after which the aggregate switches are connected to the root switch.
To connect our edge switches to our root switch, we first find how many edge switches we need to add to each aggregate switch, and how many such switches we need to use. Then, for every aggregate switch, we add the corresponding edge switches after checking if they have been previously connected or not. If not, they are connected to that aggregate switch.<br><br>
![](images/tree.png)<br><br>
The second datacenter is then created using similar steps as the first, where we create the hosts and datacenter, and then set the costs. However, this time we create a Mesh topology for this datacenter.
The mesh network uses a lot more switches and connections than the tree network, but provides theoretically higher connectivity and redundancy. To create the mesh network, we generate the edge, aggregate and root switches as before, but generate an additional layer of aggregate switches than lie in between the first layer of aggregate switches and the root switch.
To set up our connections, we start with connecting our all the hosts to all our edge switches, and all our aggregate switches to all our edge switches. Then, we connect the second layer of aggregate switches to the base layer by using a method similar to the one used in the tree topology to connect the edge switches to the aggregate switches. This helps us create redundant paths and increase the connectivity in the topology. After this, the second layer of aggregate switches are finally connected to the root switch. Additionally, we also connect each aggregate switch in the second layer to its neighbours, further increasing our number of paths.
<br>Now, our datacenter are connected to each other, and we can create our VMs and Cloudlets. To create the VMs, we use `CreateObject.createNetworkVms`, which works similar to `CreateObject.createVms` that we have used before, but generates NetworkVms instead of simple VMs. We pass parameters from the config file, and after creating the NetworkVm and setting its parameters, we get a list of NetworkVMs in return.
After this, we create our Networkcloudlets using `CreateObjects.createNetworkCloudlets`, which gives us a list of NetworkCloudlets in return, after creating them and setting their parameters.<br>
The cloudlets are then assigned to VMs, where the half the cloudlets are assigned to the first datacenter that uses a tree network using a first-fit policy, and the second half are assigned to the second datacenter which uses a mesh topology through a round-robin method.
After our cloudlets have been assigned, the program gives map/reduce tasks to the first set of cloudlets in the tree topology datacenter, and gives a diffusion task to the cloudlets in the second, mesh topology datacenter. THe map/reduce tasks are divided evenly among the cloudlets in the tree datacenter, where half the cloudlets perform the mapper task, and the other half perform the reducer task.
<br>The Vm list and Cloudlet list are submitted to the broker, and the simulation is then started.<br>The results are collected once the simualtion ends, and are placed in files for easy analysis. The power model information is stored in `CloudproviderPower.txt`, the cost information is stored in `CloudproviderCost.txt`, and the general simulation information is stored in `CloudproviderStats.csv`<br><br>
![](images/mesh.png)<br><br>
### 5) ScalingDatacenter.scala
This program creates tens of thousands of hosts, VMs and Cloudlets, and performs the simulation of scaling the VMs as more cloudlets arrive while the simulation runs.<br>
To do this, we first load our configs and create the simulation. Then, the datacenter is created like the programs above and the scheduling interval is set from the config file. We create a new broker, and then add a listener for the simulation. This enables us to add cloudlets while the simulation runs, mimicking the arrival of new cloudlets. To add new cloudlets, we get the current time of the event that triggered the method, and we check if that time is divisible by the creation interval in the configs and is also less than the specified limit. If yes, we create a list of new cloudlets and assign them various parameters such as their length, a random submission delay, utilization model, etc. The newly created cloudlets are then submitted to the broker.
<br>We create a list of new cloudlets, submit the VMs and Cloudlets to our broker, and start the simulation. Every time the listener is triggered, the method described in the lines above runs and adds new cloudlets to the simulation, which identifies if it is overloaded using the predicate defined in `isVmOverloaded`. If the CPU utilization of the VM is above 70%, and the ram utilization of the host is above 50%, the datacenter scales by adding another VM so that the new cloudlets can be assigned to that VM.
<br>Once the simulation finished running, the output is placed in files `CloudproviderStats.csv`,`CloudproviderCost.txt`, and `CloudproviderPower.txt`.

### 6) IaasPaasSaas.scala
This is the final grad-only simulation, where the program creates 3 datacenters to implement Iaas, Paas and Saas models.<br>
To achieve this, the program creates 3 datacenters, where the configurations are divided betwween the provider and the user. For Iaas, the provider defines the basic details such as the hosts and costs, along with which VM scheduler to use, and it is up to the user to define the other configurations of the VMs and cloudlets.<br>
For Paas, the provider defines everything in the Iaas model, plus the configurations of the VMs and the number of processing elements that each cloudlet gets. The user defines the details of how many VMs they want to spin up, and how many cloudlets of what length and size are required.<br>
For Saas, the cloud provider defines most of the configurations, leaving the user to only control how many cloudlets they want to run, of what length and size, effectovely allowing them only to control the number of application instances that they can run.<br>
Once the three datacenters are created, the Vms are spun up from all 3 configurations and submitted to the broker. The cloudlets are also generated from the 3 configurations and submitted to the broker. Then, the simulation is executed and the results of the simulation, cost and power are printed in the stdout.

## Results:

### 1) VMAllocation.scala
![](images/VMalloc1.png)

![](images/VMalloc2.png)

### 2) VMUtilAndSchedule.scala
![](images/VMUtil1.png)

![](images/VMUtil2.png)

### 3) CloudProvider.scala
![](images/Cloudprovider%201.png)

![](images/Cloudprovider%202.png)

![](images/Cloudprovider%203.png)

### 4) ScalingDatacenter.scala
![](images/Scaling1.png)

![](images/Scaling2.png)

![](images/Scaling3.png)

### 5) IaasPaasSaas.scala

![](images/gradsim1.png)

![](images/gradsim2.png)

![](images/gradsim3.png)


## Analysis:

### 1) VMAllocation.scala
This simulation shows us the effects of different VM Allocation policies. The first simulation shows us how the first-fit policy works, where VM0 is allocated to Host 0, VM1 is allocated to Host 0, VM2 is allocated to Host 1, VM3 is allocated to Host 1, and so on.
In first-fit, the VMs are allocated to the first host that fits our criteria of space, utilization, etc. We check the first host, and if it fits our criteria, we allocate the VM to it. If not, we move to the next host and check. This process goes on till we find a space for our VM and after it has been assigned, the process starts again for the next VM in our list.<br>
The second simulation uses a round-robin allocation to allocate VMs to hosts, where VM0 is allocated to Host 0, VM1 is allocated to Host 1, VM2 is allocated to Host 2, VM3 is allocated to VM3, and so on.
Round Robin moves through the Hosts and assigns the VMs one by one, which means that the first host gets the first VM, the second host gets the second VM, upto the last host getting its corresponding VM. Then, the algorithm loops back to assign the next VM to host 0.
THe process continues till all the hosts are assigned.

### 2) VMUtilAndSchedule.scala

### 3) CloudProvider.scala

### 4) ScalingDatacenter.scala

### 5) IaasPaasSaas.scala