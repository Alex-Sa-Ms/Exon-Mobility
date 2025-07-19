# Exon with Mobility Support

This repository is a modified fork of [Exon - Oblivious Exactly-Once Messaging Protocol](https://github.com/ziadkassam/Exon/), extended to support **mobility scenarios**. It also serves as the transport foundation for the [A3M - Messaging Middleware with Exactly-Once Semantics](https://github.com/Alex-Sa-Ms/A3M-Messaging_Middleware_With_EO) project.

## What's New

This version of Exon enables support for mobile nodes by **decoupling node identity from transport addresses**. Each node is now identified by a **unique identifier**, rather than its network address.

To facilitate discovery:
- **Static nodes** should be registered in a discovery service.
- **Dynamic (mobile) nodes** can use this service to find and communicate with static peers.
