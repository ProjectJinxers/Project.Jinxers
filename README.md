# Project Jinxers

The goal of this project is to create a decentralized platform for publishing and reviewing Markdown (or something else, but not HTML) documents. The plan is to provide importers, which convert HTML pages to (e.g.) Markdown documents, so users can view and review existing web documents in the application. The documents will be stored in IPFS. There will be users, a rating system and polls, which might even be anonymous. This data will be stored in IPFS in so called ModelState instances, which also indirectly reference the documents, as well. Their multihashes will be saved in the IOTA Tangle. There will be no servers, no databases, no cloud infrastructure, no costs. Just IPFS nodes, and, if there is no public IOTA node, that can be used reliably, IOTA nodes.

## Current state

The project is still in its early stages. Its first artifact was a UML model containing some class and activity diagrams, as well as some object diagrams for visualizing and studying some more or less complex states. There's also a lot of prose, for which it would have been too much effort to convert it to diagrams. The model has been created and edited with [StarUML](https://staruml.io/). That model is already pretty much outdated, and there are currently no plans to update it, as it already has fulfilled its main purpose: getting an overview over the required features and helping to implement them in the prototype application. That prototype application now defines the specification (which is still neither complete nor stable).

## Next steps

The current plan is to create a JavaFX prototype application as a proof of concept. The business logic, which uses [ipfs-shipyard/java-ipfs-http-client](https://github.com/ipfs-shipyard/java-ipfs-http-client) for communicating with a local IPFS node and will probably use [iotaledger/iota-java](https://github.com/iotaledger/iota-java) for communicating with a public IOTA (perma)node, has been implemented. The UI is currently being implemented. Time is limited, the matter is complex. Apart from unit-testing, none of the proposed solutions for the identified problems have been practically evaluated using IPFS and the IOTA Tangle, yet.

## Local setup

In order to run the prototype application, you can either import the project into your favorite IDE or use the gradle command line tool to execute the 'run' task.

### Debugging

If you want to debug the application in your favorite IDE without connecting the debugger to the running application, you might have to copy all dependencies into a folder in the project. The gradle task 'copyDependencies' strips the version from the jar file names and copies the renamed jars into the local-libs folder. Some original jar file names cause java module resolution errors. After executing the task and adding local-libs to the module path, there will still be some module errors. You can solve them by removing the affected copied jars. Not all jars in the folder have to be on the module path.

### IPFS Node

The application needs a running IPFS node. You can download [IPFS Desktop](https://docs.ipfs.io/install/ipfs-desktop/) or use a command line IPFS daemon. IPFS Desktop connects to a daemon, which is installed with it and which is started by it when it is started. However, the current version does not enable pubsub by default. The application relies on pubsub. You can start the deamon before starting IPFS Desktop. In fact, you don't need to start IPFS Desktop. Starting the daemon is good enough for the application. You can use this command to enable pubsub:

```
ipfs daemon --enable-pubsub-experiment
```



## Validation

One important feature of the business logic is validating incoming model states. The prototype implementation treats validation errors
as attacks. Model states, that have been created and published by legit nodes, should never cause validation errors in other nodes. As a consequence, the code assumes valid states and throws all kinds of natural runtime exceptions if that assumption is wrong. If a required condition is not met, which again should never happen if the state has been sent by a legit node, a ValidationException is thrown. It provides just enough information for debugging and fixing the problem during development or in unit tests.

### Loading objects

In order to validate model states, their objects (object graphs) have to be loaded. Loading might fail, especially if there aren't many
nodes. However, in theory this should not be a special problem, since model states are calculated by nodes, which publish their hashes. Receivers of those hashes can ask the publishers for the data. Barring internet connection issues and unless a publisher is shut down right after sending the hash, which can also be treated as some kind of network IO error, the receiver should be able to successfully download the objects from the publisher at least.

### Scaling

Another issue could be scaling. If a (sub)system has many users and contains many documents and other entities, the model state instances grow in size. Currently, model state instances are absolute, i.e. they don't just contain the difference to the previous states. They contain everything. Intuitively, this is best for small (sub)systems. Switching to relative model state instances might be an option for bigger (sub)systems.

### Light nodes

In bigger (sub)systems, it might be useful to introduce the concept of light nodes, which are nodes, that don't perform validation and
instead trust other nodes. They can still calculate and publish new model states, when the user adds a document for instance. Validator nodes could be rewarded for the work they do. This is not the main focus of this project, so feel free to work on that in your own fork. And if you create a new crypto currency, please consider calling it Jinxers ;)

## Extensions

The specification leaves some room for extensions. The main entry point for extensions is the DocumentContents class. It can be anything.

### Search Index

The specification does not contain anything regarding searching. But in combination with special dedicated accounts, DocumentContents instances (or instances of subclasses thereof) can be used to save search indices. Users will have to trust them. However, the platform already provides means to get rid of unfair search indices, since they are also documents, which can be reviewed. Without using a search index, a search algorithm has to load all documents of the current (or another) model state. Search index reviews could also utilize special DocumentContents instances (or instances of subclasses thereof), to allow for checking the claims stated in the review.

## Contributions

Feel free to fork this repo and use it as a starting point for refinement and for adding your own ideas. Pull Requests will probably not be merged, at least not in the near future. We'll see what happens. No matter who does it, we want to see the ideas come to life!

## License

>Copyright (C) 2021 ProjectJinxers
>
>This program is free software: you can redistribute it and/or modify
>it under the terms of the GNU General Public License as published by
>the Free Software Foundation, either version 3 of the License, or
>(at your option) any later version.
>
>This program is distributed in the hope that it will be useful,
>but WITHOUT ANY WARRANTY; without even the implied warranty of
>MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
>GNU General Public License for more details.
>
>You should have received a copy of the GNU General Public License
>along with this program.  If not, see <https://www.gnu.org/licenses/>.

## Author

ProjectJinxers
