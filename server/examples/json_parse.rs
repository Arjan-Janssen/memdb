extern crate memdb_lib;

use serde::Deserialize;

// Example from:
// https://stackoverflow.com/questions/30292752/how-do-i-parse-a-json-file
#[derive(Debug, Deserialize)]
#[serde(rename_all = "PascalCase")]
#[allow(dead_code)]
struct Person {
    first_name: String,
    last_name: String,
    age: u8,
    address: Address,
    phone_numbers: Vec<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "PascalCase")]
#[allow(dead_code)]
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

fn main() {
    let server_thread = memdb_lib::server::run().expect("Unable to run server");

    memdb_lib::server::send_marker("begin");
    parse_json();
    memdb_lib::server::send_marker("end");

    memdb_lib::server::send_terminate();

    if server_thread.join().is_err() {
        println!("Unable to join server thread");
    }
}
