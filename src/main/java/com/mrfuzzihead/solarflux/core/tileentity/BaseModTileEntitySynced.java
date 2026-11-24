package com.mrfuzzihead.solarflux.core.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

public class BaseModTileEntitySynced extends BaseModTileEntity {

    /**
     * Prepare a Packet to send the state of the {@link TileEntity} to the Client.
     *
     * <p>
     * Only the display state goes over the wire; disk-only data (like inventories) stays out of the packet.
     * </p>
     */
    @Override
    public Packet getDescriptionPacket() {
        return new S35PacketUpdateTileEntity(x(), y(), z(), 1, getSyncTag());
    }

    /**
     * Returns the NBT that {@link #getDescriptionPacket()} sends. Public so the tests can assert the payload without
     * going through a packet whose method names differ between the development and production mappings.
     */
    public NBTTagCompound getSyncTag() {
        NBTTagCompound nbt = new NBTTagCompound();
        addSyncDataToNBT(nbt);
        return nbt;
    }

    /**
     * Applies the payload of a description packet. Public for the same reason as {@link #getSyncTag()}.
     */
    public void applySyncTag(NBTTagCompound pNBT) {
        loadSyncDataFromNBT(pNBT);
    }

    /**
     * Process a Packet to update the state of the {@link TileEntity} on the Client
     */
    @Override
    public void onDataPacket(NetworkManager pNet, S35PacketUpdateTileEntity pPacket) {
        super.onDataPacket(pNet, pPacket);
        applySyncTag(pPacket.func_148857_g());
    }

    /**
     * Writes what the client needs to display this block. Defaults to the full data; override to keep large data off
     * the network.
     */
    protected void addSyncDataToNBT(NBTTagCompound pNBT) {
        addDataToNBT(pNBT);
    }

    /**
     * Applies a description packet. Defaults to the full loader; override together with {@link #addSyncDataToNBT}.
     */
    protected void loadSyncDataFromNBT(NBTTagCompound pNBT) {
        loadDataFromNBT(pNBT);
    }
}
