# Project Jinxers

The goal of this project is to create a decentralized platform for publishing and reviewing Markdown documents. The plan is to provide an importer, which converts HTML pages to Markdown documents, so users can view and review existing web documents in the application. The documents will be stored in IPFS and their multihashes will be saved in the IOTA Tangle utilizing zero-value transactions. There will be users, a rating system and polls, which might even be anonymous. This data will be stored in IPFS, as well. There will be no servers, no databases, no cloud infrastructure, no costs. Just IPFS nodes, and, if there is no public IOTA node, that can be used reliably, IOTA nodes.

## Current state

The project is in its early stages. Currently, there is a UML model containing some class and activity diagrams, as well as some object diagrams for visualizing and studying some more or less complex states. There's also a lot of prose, for which it would be too much effort to convert it to diagrams. The model has been created and edited with [StarUML](https://staruml.io/).

## Next steps

The requirements have been identified and modeled. Almost all problems, that came up, have been solved. At least theoretically. Next up is creating a prototype as a proof of concept. The current plan is to create a Java application, which uses [ipfs-shipyard/java-ipfs-http-client](https://github.com/ipfs-shipyard/java-ipfs-http-client) for communicating with a local IPFS node and [iotaledger/iota-java](https://github.com/iotaledger/iota-java) for communicating with a public IOTA (perma)node. Time is limited, the matter is complex. None of the proposed solutions for the identified problems have been practically evaluated, yet. There will probably be lots and lots of JUnit tests before even thinking about the UI. So please don't expect too much, but we'll try our best :)

## Contributions

Feel free to fork this repo and use the model as a starting point for refinement and for adding your own ideas. Pull Requests will probably not be merged, at least not in the near future. We'll see what happens. No matter who does it, we want to see the ideas come to life!

## Author

ProjectJinxers
