//! # memdb_lib
//!
//! `memdb_lib` is a library that hooks into the Rust memory allocator and records all
//! heap operations.
//!
//! A server listens on a port until a client establishes a connection.
//! When the connection is established, the program resumes and any following heap operation
//! will be sent to connected client.
mod hook;
pub mod server;
