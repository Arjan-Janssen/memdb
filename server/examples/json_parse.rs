include!("checked_wrapper.rs");

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
    memdb_lib::server::send_marker("person");
}

fn main() {
    let server_handle = checked_run_with_default_address();
    checked_send_marker("begin");
    parse_json();
    checked_send_marker("end");
    checked_send_terminate();
    checked_join_thread(server_handle);
}
