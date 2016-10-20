# ShutApp

Wearable apps are comprised of 2 different apps: one for the watch (`app`), and one for the phone (`handheld`). Both apps work like a distributed system, with the communication happenning over a number of different APIs. We use the a simple message passing API, called `MessageApi` under the standard Android wearable package.

Before doing anything, we need to initialize a `GoogleApiClient` with all the services that we need. In our case, we need both `LocationServices` and the `Wearable` API. 

```
myApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
```
                
After we get a handle on the `GoogleApiClient`, we initialize `MessageApi`. To do that we first need to figure out which `Nodes` are connected in our system. Realistically speaking, we should only have one node connected at any given time: if we're calling from the watch, we should see the phone, and vice versa.

```
NodeApi.GetConnectedNodesResult nodes = 
	Wearable.NodeApi.getConnectedNodes(mClient).await();
```

Now, we can use `MessageApi`. When we determine which node to communicate with, we can simply use this call:

```
result = Wearable.MessageApi.sendMessage(
                                myApiClient,
                                node.getId(),
                                path,
                                message.getBytes("UTF8"))
                        .await();
```
Where path is a string that specifies the endpoint, and message is the string we're sending.

After this point the `handheld` portion takes over. To register a listener for the messages arriving from `MessageApi`, we need to initialize a service that extends `WearableListenerService`. The method `onFunctionReceived` is the point at which we receive a message. Note that wearables can't connect to the Internet directly, so all API calls should happen through the `handheld` service.

In **ShutApp**, we use `MessageApi` to pass the current location of the watch to the phone. The `handheld` service receives the location as a latitude and longitude pair, and proceeds to make our calls to the TransLoc API.

**ShutApp** makes two calls to the TransLoc API: one call to figure out where the nearest stop is, and get all the routes that pass through that stop, the other is figure out which shuttles pass through that stop for each line. Afterwards, we send the results back to the watch. The process is similar to that of sending information to the phone, but in the reverse direction.