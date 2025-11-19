package com.telemessage.simulators.controllers.health;


import com.telemessage.simulators.common.conf.EnvConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/health")
public class Health {

    EnvConfiguration env;

    public Health(EnvConfiguration env) {
        this.env = env;
    }

    @RequestMapping(method = RequestMethod.GET,
            path = "/ok",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Description("Test health get OK")
    public ResponseEntity<String> health() {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body("[OK]");
    }

    @RequestMapping(method = RequestMethod.GET, path = "/OK", produces = MediaType.APPLICATION_JSON_VALUE,
            name = "HEALTH_OK")
    @Description("Test health get OK \uD83E\uDD2C")
    public ResponseEntity<String> healthOK() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body("{OK \uD83E\uDD2C}");
    }


    @RequestMapping(method = RequestMethod.GET, path = "/Ok", produces = MediaType.APPLICATION_JSON_VALUE,
            name = "HEALTH_OK")
    @Description("Test health get OK \uD83D\uDE05: With object cached")
    public ResponseEntity<String> healthOk() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body("{Ok \uD83D\uDE05}");
    }


}
