# Azure Resource Listing Application

This Spring Boot application lists all the virtual machines, services, and servers available in a single Azure subscription.

## Features

- Fetches and displays a list of Azure resources including virtual machines, services, and servers.
- Utilizes Azure SDK for Java to interact with Azure services.
- RESTful API to access resource information.

## Prerequisites

- Java 11 or higher
- Maven
- Azure subscription with appropriate permissions

## Setup Instructions

1. Clone the repository:
   ```
   git clone <repository-url>
   ```

2. Navigate to the project directory:
   ```
   cd azure-resource-listing-app
   ```

3. Update the `application.properties` file located in `src/main/resources/` with your Azure credentials and subscription details.

4. Build the project using Maven:
   ```
   mvn clean install
   ```

5. Run the application:
   ```
   mvn spring-boot:run
   ```

## Usage

Once the application is running, you can access the API to list Azure resources at:
```
http://localhost:8080/api/resources
```

## Contributing

Contributions are welcome! Please open an issue or submit a pull request for any improvements or bug fixes.

## License

This project is licensed under the MIT License. See the LICENSE file for more details.