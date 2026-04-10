## 1. Fix notification-service ObjectMapper

- [x] 1.1 In `notification-service/src/main/java/com/example/notification/kafka/KafkaConsumerConfig.java`, replace `return new ObjectMapper()` with `return JsonMapper.builder().findAndAddModules().build()` and add `import com.fasterxml.jackson.databind.json.JsonMapper`
- [x] 1.2 Remove unused `import com.fasterxml.jackson.databind.ObjectMapper` if no longer needed (keep if used elsewhere in the class)

## 2. Fix friendship-service ObjectMapper

- [x] 2.1 In `friendship-service/src/main/java/com/example/friendship/configuration/JacksonConfig.java`, replace `return new ObjectMapper()` with `return JsonMapper.builder().findAndAddModules().build()` and add `import com.fasterxml.jackson.databind.json.JsonMapper`
- [x] 2.2 Remove unused `import com.fasterxml.jackson.databind.ObjectMapper` if no longer needed

## 3. Verification

- [x] 3.1 Run `.\gradlew.bat :notification-service:compileJava :friendship-service:compileJava` and confirm BUILD SUCCESSFUL
- [x] 3.2 Run `.\gradlew.bat :notification-service:test :friendship-service:test` and confirm all tests pass
- [x] 3.3 Manually verify a notification REST endpoint returns `createdAt` as an ISO-8601 string (or write a slice test asserting the JSON field is a string)
