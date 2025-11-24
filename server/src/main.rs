mod hook;
mod server;

use serde::Deserialize;

// Example from:
// https://stackoverflow.com/questions/30292752/how-do-i-parse-a-json-file
#[derive(Debug, Deserialize)]
#[serde(rename_all = "PascalCase")]
struct Person {
    first_name: String,
    last_name: String,
    age: u8,
    address: Address,
    phone_numbers: Vec<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "PascalCase")]
struct Address {
    street: String,
    city: String,
    country: String,
}

fn parse_json() {
    let the_file = r#"{
        "FirstName": "John",
        "LastName": "Doe",
        "Age": 43,
        "Address": {
            "Street": "Downing Street 10",
            "City": "London",
            "Country": "Great Britain"
        },
        "PhoneNumbers": [
            "+44 1234567",
            "+44 2345678"
        ]
    }"#;

    let person: Person = serde_json::from_str(the_file).expect("JSON was not well-formatted");
    println!("{:?}", person);
}

fn growing_vec() {
    let mut growing_vec = vec![1, 2, 3];
    for i in 1..10000 {
        growing_vec.push(i);
    }
}

fn main() {
    println!("memdb (c) 2025 by Arjan Janssen");

    let server_thread = server::run();
    server::send_marker("begin");
    growing_vec();

    server::send_marker("parsing");
    parse_json();
    server::send_marker("end");

    println!("Sending terminate signal to server thread!");
    server::send_terminate();

    let join_result = server_thread.unwrap().join();
    if join_result.is_err() {
        println!("Error joining thread");
        return;
    }

    println!("Closing...");
}
