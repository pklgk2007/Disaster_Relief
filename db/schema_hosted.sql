
-- ---------- People ----------
CREATE TABLE Person (
  person_id INT AUTO_INCREMENT PRIMARY KEY,
  first_name VARCHAR(50) NOT NULL,
  last_name VARCHAR(50) NOT NULL,
  email VARCHAR(100) UNIQUE,
  date_of_birth DATE
);
CREATE TABLE Person_Phone (
  person_id INT NOT NULL,
  phone VARCHAR(15) NOT NULL,
  PRIMARY KEY (person_id, phone),
  FOREIGN KEY (person_id) REFERENCES Person(person_id) ON DELETE CASCADE
);
CREATE TABLE Emergency_Contact (
  contact_id INT AUTO_INCREMENT PRIMARY KEY,
  person_id INT NOT NULL,
  contact_name VARCHAR(100) NOT NULL,
  phone VARCHAR(15) NOT NULL,
  relationship VARCHAR(40),
  FOREIGN KEY (person_id) REFERENCES Person(person_id) ON DELETE CASCADE
);

-- ---------- Places ----------
CREATE TABLE Location (
  location_id INT AUTO_INCREMENT PRIMARY KEY,
  district VARCHAR(60) NOT NULL,
  area VARCHAR(80) NOT NULL,
  pincode CHAR(6) NOT NULL
);
CREATE TABLE Shelter (
  shelter_id INT AUTO_INCREMENT PRIMARY KEY,
  location_id INT NOT NULL,
  name VARCHAR(100) NOT NULL,
  capacity INT NOT NULL CHECK (capacity > 0),
  status ENUM('OPEN','FULL','CLOSED') NOT NULL DEFAULT 'OPEN',
  FOREIGN KEY (location_id) REFERENCES Location(location_id)
);
CREATE TABLE Disaster_Event (
  event_id INT AUTO_INCREMENT PRIMARY KEY,
  location_id INT NOT NULL,
  type VARCHAR(40) NOT NULL,
  severity ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL,
  start_date DATE NOT NULL,
  FOREIGN KEY (location_id) REFERENCES Location(location_id)
);

-- ---------- Staff (Person specialisations) ----------
CREATE TABLE Volunteer (
  volunteer_id INT AUTO_INCREMENT PRIMARY KEY,
  person_id INT NOT NULL UNIQUE,
  skill VARCHAR(100),
  availability VARCHAR(100),
  shelter_id INT NULL,
  FOREIGN KEY (person_id) REFERENCES Person(person_id) ON DELETE CASCADE,
  FOREIGN KEY (shelter_id) REFERENCES Shelter(shelter_id) ON DELETE SET NULL
);
CREATE TABLE Coordinator (
  coordinator_id INT AUTO_INCREMENT PRIMARY KEY,
  person_id INT NOT NULL UNIQUE,
  role VARCHAR(60) NOT NULL,
  FOREIGN KEY (person_id) REFERENCES Person(person_id) ON DELETE CASCADE
);

-- ---------- Supplies ----------
CREATE TABLE Item_Category (
  category_id INT AUTO_INCREMENT PRIMARY KEY,
  category_name VARCHAR(60) NOT NULL UNIQUE
);
CREATE TABLE Supply_Item (
  item_id INT AUTO_INCREMENT PRIMARY KEY,
  category_id INT NOT NULL,
  item_name VARCHAR(80) NOT NULL UNIQUE,
  unit VARCHAR(20) NOT NULL,
  expiry_days INT NULL,
  FOREIGN KEY (category_id) REFERENCES Item_Category(category_id)
);
CREATE TABLE Shelter_Stock (
  stock_id INT AUTO_INCREMENT PRIMARY KEY,
  shelter_id INT NOT NULL,
  item_id INT NOT NULL,
  quantity INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
  last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE (shelter_id, item_id),
  FOREIGN KEY (shelter_id) REFERENCES Shelter(shelter_id) ON DELETE CASCADE,
  FOREIGN KEY (item_id) REFERENCES Supply_Item(item_id)
);

-- ---------- Donations ----------
CREATE TABLE Donor (
  donor_id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(100) UNIQUE,
  phone VARCHAR(15)
);
CREATE TABLE Donation (
  donation_id INT AUTO_INCREMENT PRIMARY KEY,
  donor_id INT NOT NULL,
  item_id INT NOT NULL,
  quantity INT NOT NULL CHECK (quantity > 0),
  donated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (donor_id) REFERENCES Donor(donor_id),
  FOREIGN KEY (item_id) REFERENCES Supply_Item(item_id)
);

-- ---------- Relief requests & dispatch ----------
CREATE TABLE Relief_Request (
  request_id INT AUTO_INCREMENT PRIMARY KEY,
  event_id INT NOT NULL,
  shelter_id INT NOT NULL,
  priority ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL DEFAULT 'MEDIUM',
  status ENUM('PENDING','APPROVED','DISPATCHED','FULFILLED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (event_id) REFERENCES Disaster_Event(event_id),
  FOREIGN KEY (shelter_id) REFERENCES Shelter(shelter_id)
);
CREATE TABLE Request_Item (
  request_item_id INT AUTO_INCREMENT PRIMARY KEY,
  request_id INT NOT NULL,
  item_id INT NOT NULL,
  qty_needed INT NOT NULL CHECK (qty_needed > 0),
  UNIQUE (request_id, item_id),
  FOREIGN KEY (request_id) REFERENCES Relief_Request(request_id) ON DELETE CASCADE,
  FOREIGN KEY (item_id) REFERENCES Supply_Item(item_id)
);
CREATE TABLE Dispatch (
  dispatch_id INT AUTO_INCREMENT PRIMARY KEY,
  request_id INT NOT NULL,
  location_id INT NOT NULL,
  sent_at DATETIME NULL,
  delivered_at DATETIME NULL,
  status ENUM('PREPARING','IN_TRANSIT','DELIVERED','FAILED') NOT NULL DEFAULT 'PREPARING',
  FOREIGN KEY (request_id) REFERENCES Relief_Request(request_id) ON DELETE CASCADE,
  FOREIGN KEY (location_id) REFERENCES Location(location_id)
);
CREATE TABLE Dispatch_Log (
  log_id INT AUTO_INCREMENT PRIMARY KEY,
  dispatch_id INT NOT NULL,
  log_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  remark VARCHAR(255),
  FOREIGN KEY (dispatch_id) REFERENCES Dispatch(dispatch_id) ON DELETE CASCADE
);

-- ---------- Additions for login + volunteer tasks ----------
CREATE TABLE User_Account (
  user_id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role ENUM('ADMIN','COORDINATOR','VOLUNTEER') NOT NULL,
  person_id INT NULL UNIQUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (person_id) REFERENCES Person(person_id) ON DELETE CASCADE
);
CREATE TABLE Volunteer_Task (
  task_id INT AUTO_INCREMENT PRIMARY KEY,
  volunteer_id INT NOT NULL,
  request_id INT NULL,
  title VARCHAR(120) NOT NULL,
  description VARCHAR(500),
  status ENUM('ASSIGNED','IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'ASSIGNED',
  assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (volunteer_id) REFERENCES Volunteer(volunteer_id) ON DELETE CASCADE,
  FOREIGN KEY (request_id) REFERENCES Relief_Request(request_id) ON DELETE SET NULL
);

-- Public "Want to join" form submissions
CREATE TABLE Volunteer_Application (
  app_id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  email VARCHAR(100) NOT NULL,
  skills VARCHAR(200) NOT NULL,
  message VARCHAR(500),
  status ENUM('NEW','CONTACTED') NOT NULL DEFAULT 'NEW',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------- Seed data ----------
INSERT INTO Location (district, area, pincode) VALUES
  ('Patna','Gandhi Maidan','800001'),
  ('Kamrup','Guwahati Central','781001'),
  ('Wayanad','Kalpetta','673121');
INSERT INTO Shelter (location_id, name, capacity, status) VALUES
  (1,'Gandhi Maidan Relief Camp',400,'OPEN'),
  (1,'Patna Govt School Shelter',150,'OPEN'),
  (2,'Guwahati Community Hall',250,'OPEN'),
  (3,'Kalpetta Town Hall',200,'OPEN');
INSERT INTO Disaster_Event (location_id, type, severity, start_date) VALUES
  (1,'Flood','HIGH','2026-10-01'),
  (2,'Landslide','CRITICAL','2026-10-10'),
  (3,'Cyclone','MEDIUM','2026-10-15');
INSERT INTO Item_Category (category_name) VALUES ('Food'),('Water'),('Medical'),('Clothing & Bedding'),('Shelter Supplies');
INSERT INTO Supply_Item (category_id, item_name, unit, expiry_days) VALUES
  (1,'Rice','kg',365),
  (2,'Drinking Water','litre',180),
  (3,'First Aid Kit','box',730),
  (3,'ORS Packets','packet',540),
  (4,'Blanket','piece',NULL),
  (5,'Tarpaulin Sheet','piece',NULL);
INSERT INTO Shelter_Stock (shelter_id, item_id, quantity) VALUES
  (1,1,300),(1,2,800),(1,5,120),
  (2,1,80),(2,3,10),
  (3,2,200),(3,4,150),
  (4,6,40);
