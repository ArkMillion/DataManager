db = db.getSiblingDB("test");

db.createUser({
  user: "test",
  pwd: "dm_mongo_pwd",
  roles: [
    { role: "readWrite", db: "test" },
    { role: "dbAdmin", db: "test" }
  ]
});
