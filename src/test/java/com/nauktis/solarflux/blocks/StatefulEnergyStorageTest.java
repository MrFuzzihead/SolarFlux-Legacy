package com.nauktis.solarflux.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.Test;

import com.nauktis.solarflux.reference.NBTConstants;

import cofh.api.energy.IEnergyReceiver;

public class StatefulEnergyStorageTest {

    @Test
    public void capacityOnlyConstructorUsesTheCapacityForBothTransferDirections() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(1000);

        assertEquals(1000, storage.getMaxEnergyStored());
        assertEquals(1000, storage.getMaxTransferReceive());
        assertEquals(1000, storage.getMaxTransferExtract());
        assertEquals(0, storage.getEnergyStored());
    }

    @Test
    public void capacityAndTransferConstructorUsesTheTransferForBothDirections() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(1000, 40);

        assertEquals(1000, storage.getMaxEnergyStored());
        assertEquals(40, storage.getMaxTransferReceive());
        assertEquals(40, storage.getMaxTransferExtract());
    }

    @Test
    public void setMaxTransferUpdatesBothDirections() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(1000);

        storage.setMaxTransfer(15);

        assertEquals(15, storage.getMaxTransferReceive());
        assertEquals(15, storage.getMaxTransferExtract());
    }

    @Test
    public void receiveIsLimitedByFreeSpace() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(100, 1000);
        storage.setEnergyStored(90);

        assertEquals(10, storage.receiveEnergy(50, false));
        assertEquals(100, storage.getEnergyStored());
    }

    @Test
    public void receiveIsLimitedByTheReceiveTransferRate() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(1000, 30);

        assertEquals(30, storage.receiveEnergy(500, false));
        assertEquals(30, storage.getEnergyStored());
    }

    @Test
    public void receiveTreatsNegativeRequestsAsZero() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(1000, 30);

        assertEquals(0, storage.receiveEnergy(-50, false));
        assertEquals(0, storage.getEnergyStored());
    }

    @Test
    public void simulatedReceiveReportsTheOfferWithoutStoringIt() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(1000, 30);

        assertEquals(30, storage.receiveEnergy(500, true));
        assertEquals(0, storage.getEnergyStored());
    }

    @Test
    public void extractIsLimitedByTheStoredEnergy() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(1000, 1000);
        storage.setEnergyStored(12);

        assertEquals(12, storage.extractEnergy(50, false));
        assertEquals(0, storage.getEnergyStored());
    }

    @Test
    public void extractIsLimitedByTheExtractTransferRate() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(1000, 1000, 7);
        storage.setEnergyStored(500);

        assertEquals(7, storage.extractEnergy(50, false));
        assertEquals(493, storage.getEnergyStored());
    }

    @Test
    public void extractTreatsNegativeRequestsAsZero() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(1000);
        storage.setEnergyStored(100);

        assertEquals(0, storage.extractEnergy(-5, false));
        assertEquals(100, storage.getEnergyStored());
    }

    @Test
    public void simulatedExtractReportsTheOfferWithoutReleasingIt() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(1000);
        storage.setEnergyStored(100);

        assertEquals(100, storage.extractEnergy(100, true));
        assertEquals(100, storage.getEnergyStored());
    }

    @Test
    public void setEnergyStoredClampsToTheCapacity() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(500);

        storage.setEnergyStored(900);
        assertEquals(500, storage.getEnergyStored());

        storage.setEnergyStored(-900);
        assertEquals(0, storage.getEnergyStored());
    }

    @Test
    public void setMaxEnergyStoredShrinksTheStoredEnergy() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(1000);
        storage.setEnergyStored(800);

        storage.setMaxEnergyStored(200);

        assertEquals(200, storage.getMaxEnergyStored());
        assertEquals(200, storage.getEnergyStored());
    }

    @Test
    public void setMaxEnergyStoredKeepsTheStoredEnergyWhenItStillFits() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(100);
        storage.setEnergyStored(50);

        storage.setMaxEnergyStored(500);

        assertEquals(500, storage.getMaxEnergyStored());
        assertEquals(50, storage.getEnergyStored());
    }

    @Test
    public void isFullOnlyWhenTheStoredEnergyMatchesTheCapacity() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(10);

        assertFalse(storage.isFull());
        storage.setEnergyStored(9);
        assertFalse(storage.isFull());
        storage.setEnergyStored(10);
        assertTrue(storage.isFull());
    }

    @Test
    public void getMaxReceiveAndExtractReportTheRemainingHeadroom() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(100, 40, 60);
        storage.setEnergyStored(90);

        assertEquals(10, storage.getMaxReceive());
        assertEquals(60, storage.getMaxExtract());
    }

    @Test
    public void nbtRoundTripPreservesTheStoredEnergy() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(1000);
        storage.setEnergyStored(321);
        NBTTagCompound tag = new NBTTagCompound();

        storage.writeToNBT(tag);

        assertEquals(321, tag.getInteger(NBTConstants.ENERGY));

        StatefulEnergyStorage restored = new StatefulEnergyStorage(1000);
        restored.readFromNBT(tag);
        assertEquals(321, restored.getEnergyStored());
    }

    @Test
    public void readFromNbtTreatsAMissingEntryAsZero() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(1000);
        storage.setEnergyStored(321);

        storage.readFromNBT(new NBTTagCompound());

        assertEquals(0, storage.getEnergyStored());
    }

    @Test
    public void sendMaxToExtractsExactlyWhatTheReceiverAccepted() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(1000, 1000);
        storage.setEnergyStored(100);
        IEnergyReceiver receiver = mock(IEnergyReceiver.class);
        when(receiver.receiveEnergy(ForgeDirection.NORTH, 100, false)).thenReturn(40);

        assertEquals(40, storage.sendMaxTo(receiver, ForgeDirection.NORTH));
        assertEquals(60, storage.getEnergyStored());
    }

    @Test
    public void sendMaxToAsksTheReceiverForTheExtractableAmountOnly() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(1000, 1000);
        storage.setEnergyStored(100);
        IEnergyReceiver receiver = mock(IEnergyReceiver.class);
        when(receiver.receiveEnergy(eq(ForgeDirection.NORTH), anyInt(), anyBoolean())).thenReturn(25);

        assertEquals(25, storage.sendMaxTo(receiver, ForgeDirection.NORTH));
        assertEquals(75, storage.getEnergyStored());
        // Never a simulated call: the panel only offers what the receiver can actually take.
        verify(receiver).receiveEnergy(ForgeDirection.NORTH, 100, false);
    }

    @Test
    public void sendMaxToOffersNothingWhenEmpty() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(1000);
        IEnergyReceiver receiver = mock(IEnergyReceiver.class);

        assertEquals(0, storage.sendMaxTo(receiver, ForgeDirection.NORTH));
        assertEquals(0, storage.getEnergyStored());
        verify(receiver).receiveEnergy(ForgeDirection.NORTH, 0, false);
    }

    @Test
    public void autoBalanceMovesHalfTheDifferenceToTheEmptierStorage() {
        StatefulEnergyStorage rich = new StatefulEnergyStorage(1000, 1000);
        StatefulEnergyStorage poor = new StatefulEnergyStorage(1000, 1000);
        rich.setEnergyStored(100);
        poor.setEnergyStored(20);

        assertEquals(40, rich.autoBalanceEnergy(poor));
        assertEquals(60, rich.getEnergyStored());
        assertEquals(60, poor.getEnergyStored());
    }

    @Test
    public void autoBalanceWorksWhenCalledOnTheEmptierStorage() {
        StatefulEnergyStorage rich = new StatefulEnergyStorage(1000, 1000);
        StatefulEnergyStorage poor = new StatefulEnergyStorage(1000, 1000);
        rich.setEnergyStored(100);
        poor.setEnergyStored(20);

        assertEquals(40, poor.autoBalanceEnergy(rich));
        assertEquals(60, rich.getEnergyStored());
        assertEquals(60, poor.getEnergyStored());
    }

    @Test
    public void autoBalanceIsANoOpForEqualStorages() {
        StatefulEnergyStorage first = new StatefulEnergyStorage(1000);
        StatefulEnergyStorage second = new StatefulEnergyStorage(1000);
        first.setEnergyStored(50);
        second.setEnergyStored(50);

        assertEquals(0, first.autoBalanceEnergy(second));
        assertEquals(50, first.getEnergyStored());
        assertEquals(50, second.getEnergyStored());
    }

    @Test
    public void autoBalanceSkipsAFullTarget() {
        StatefulEnergyStorage rich = new StatefulEnergyStorage(1000, 1000);
        StatefulEnergyStorage full = new StatefulEnergyStorage(100, 1000);
        rich.setEnergyStored(500);
        full.setEnergyStored(100);

        assertEquals(0, rich.autoBalanceEnergy(full));
        assertEquals(500, rich.getEnergyStored());
    }

    @Test
    public void autoBalanceWithATransferSpeedRestoresTheOriginalTransferLimits() {
        StatefulEnergyStorage rich = new StatefulEnergyStorage(1000, 1000);
        StatefulEnergyStorage poor = new StatefulEnergyStorage(1000, 1000);
        rich.setEnergyStored(800);
        poor.setEnergyStored(0);

        rich.autoBalanceEnergy(poor, 100);

        assertEquals(400, rich.getEnergyStored());
        assertEquals(400, poor.getEnergyStored());
        assertEquals(1000, rich.getMaxTransferReceive());
        assertEquals(1000, rich.getMaxTransferExtract());
        assertEquals(1000, poor.getMaxTransferReceive());
        assertEquals(1000, poor.getMaxTransferExtract());
    }

    @Test
    public void toStringReportsTheState() {
        StatefulEnergyStorage storage = new StatefulEnergyStorage(1000, 40, 30);
        storage.setEnergyStored(7);

        String text = storage.toString();

        assertTrue(text, text.contains("energy=7"));
        assertTrue(text, text.contains("capacity=1000"));
        assertTrue(text, text.contains("maxTransferReceive=40"));
        assertTrue(text, text.contains("maxTransferExtract=30"));
    }
}
