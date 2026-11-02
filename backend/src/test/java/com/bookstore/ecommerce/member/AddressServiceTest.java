package com.bookstore.ecommerce.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.member.MemberDtos.Address;
import com.bookstore.ecommerce.member.MemberDtos.AddressRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddressServiceTest {

    private MemberRepository repository;
    private AddressService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID addressId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(MemberRepository.class);
        service = new AddressService(repository);
    }

    private Address createAddress(UUID id, boolean isDefault) {
        return new Address(id, "Home", "Alice", "Smith", "123 Main St", "Apt 4", "Austin", "TX", "78701", "US",
                "alice@example.com", "+1", "5551234", isDefault, OffsetDateTime.now());
    }

    private AddressRequest createRequest(boolean isDefault) {
        return new AddressRequest("Home", "Alice", "Smith", "123 Main St", "Apt 4", "Austin", "TX", "78701", "US",
                "alice@example.com", "+1", "5551234", isDefault);
    }

    @Test
    void listReturnsAllUserAddresses() {
        Address addr = createAddress(addressId, true);
        when(repository.listAddresses(userId)).thenReturn(List.of(addr));

        List<Address> result = service.list(userId);

        assertThat(result).hasSize(1).contains(addr);
    }

    @Test
    void getReturnsAddressWhenFound() {
        Address addr = createAddress(addressId, true);
        when(repository.findAddress(userId, addressId)).thenReturn(Optional.of(addr));

        Address result = service.get(userId, addressId);

        assertThat(result).isEqualTo(addr);
    }

    @Test
    void getThrowsNotFoundWhenAddressMissing() {
        when(repository.findAddress(userId, addressId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(userId, addressId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.ADDRESS_NOT_FOUND));
    }

    @Test
    void createFirstAddressSetsDefaultAutomatically() {
        AddressRequest request = createRequest(false);
        Address created = createAddress(addressId, true);

        when(repository.countAddresses(userId)).thenReturn(0L);
        when(repository.insertAddress(userId, request, true)).thenReturn(addressId);
        when(repository.findAddress(userId, addressId)).thenReturn(Optional.of(created));

        Address result = service.create(userId, request);

        assertThat(result.isDefault()).isTrue();
        verify(repository).clearDefault(userId);
        verify(repository).insertAddress(userId, request, true);
    }

    @Test
    void createSubsequentAddressWithDefaultClearsExistingDefaults() {
        AddressRequest request = createRequest(true);
        Address created = createAddress(addressId, true);

        when(repository.countAddresses(userId)).thenReturn(2L);
        when(repository.insertAddress(userId, request, true)).thenReturn(addressId);
        when(repository.findAddress(userId, addressId)).thenReturn(Optional.of(created));

        Address result = service.create(userId, request);

        assertThat(result.isDefault()).isTrue();
        verify(repository).clearDefault(userId);
        verify(repository).insertAddress(userId, request, true);
    }

    @Test
    void createSubsequentAddressWithoutDefaultDoesNotClearDefaults() {
        AddressRequest request = createRequest(false);
        Address created = createAddress(addressId, false);

        when(repository.countAddresses(userId)).thenReturn(2L);
        when(repository.insertAddress(userId, request, false)).thenReturn(addressId);
        when(repository.findAddress(userId, addressId)).thenReturn(Optional.of(created));

        Address result = service.create(userId, request);

        assertThat(result.isDefault()).isFalse();
        verify(repository, never()).clearDefault(userId);
        verify(repository).insertAddress(userId, request, false);
    }

    @Test
    void updateAddressMaintainsDefaultWhenNotSpecified() {
        Address current = createAddress(addressId, true);
        AddressRequest request = createRequest(false);
        Address updated = createAddress(addressId, true);

        when(repository.findAddress(userId, addressId)).thenReturn(Optional.of(current), Optional.of(updated));

        Address result = service.update(userId, addressId, request);

        assertThat(result.isDefault()).isTrue();
        verify(repository).updateAddress(addressId, request, true);
    }

    @Test
    void updateAddressToDefaultClearsExistingDefaults() {
        Address current = createAddress(addressId, false);
        AddressRequest request = createRequest(true);
        Address updated = createAddress(addressId, true);

        when(repository.findAddress(userId, addressId)).thenReturn(Optional.of(current), Optional.of(updated));

        Address result = service.update(userId, addressId, request);

        assertThat(result.isDefault()).isTrue();
        verify(repository).clearDefault(userId);
        verify(repository).updateAddress(addressId, request, true);
    }

    @Test
    void deleteRemovesAddressWhenFound() {
        Address current = createAddress(addressId, false);
        when(repository.findAddress(userId, addressId)).thenReturn(Optional.of(current));

        service.delete(userId, addressId);

        verify(repository).deleteAddress(addressId);
    }

    @Test
    void deleteThrowsNotFoundWhenMissing() {
        when(repository.findAddress(userId, addressId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, addressId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.ADDRESS_NOT_FOUND));
    }

    @Test
    void setDefaultClearsAndSetsNewDefault() {
        Address current = createAddress(addressId, false);
        Address updated = createAddress(addressId, true);

        when(repository.findAddress(userId, addressId)).thenReturn(Optional.of(current), Optional.of(updated));

        Address result = service.setDefault(userId, addressId);

        assertThat(result.isDefault()).isTrue();
        verify(repository).clearDefault(userId);
        verify(repository).setDefault(addressId);
    }

    @Test
    void findOwnAddressReturnsOptional() {
        Address current = createAddress(addressId, true);
        when(repository.findAddress(userId, addressId)).thenReturn(Optional.of(current));

        Optional<Address> result = service.findOwnAddress(userId, addressId);

        assertThat(result).contains(current);
    }

    @Test
    void defaultAddressCountryReturnsCountry() {
        when(repository.defaultAddressCountry(userId)).thenReturn(Optional.of("US"));

        Optional<String> result = service.defaultAddressCountry(userId);

        assertThat(result).contains("US");
    }
}
