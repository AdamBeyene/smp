package com.telemessage.simulators.smpp.wrapper;

import com.logica.smpp.pdu.Address;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AddressWrapper {
    String value;
    byte ton;
    byte npi;

    public AddressWrapper() {}

    public AddressWrapper(String value, byte ton, byte npi) {
        this.value = value;
        this.ton = ton;
        this.npi = npi;
    }

    public static AddressWrapper create(Address address) {
        if (address == null)
            return null;
        return new AddressWrapper(address.getAddress(), address.getTon(), address.getNpi());
    }

}
