/*
 * Copyright (C) 2014 - 2020 | Alexander01998 | All rights reserved.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.InsecureTextureException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.authlib.minecraft.MinecraftSessionService;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.util.Identifier;
import net.wurstclient.util.json.JsonUtils;

@Mixin(PlayerSkinProvider.class)
public class PlayerSkinProviderMixin
{
	@Shadow
	@Final
	private MinecraftSessionService sessionService;
	
	@Shadow
	@Final
	private static ExecutorService EXECUTOR_SERVICE;
	
	private static JsonObject capes;
	
	@Inject(at = {@At("HEAD")},
		method = {
			"loadSkin(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/client/texture/PlayerSkinProvider$SkinTextureAvailableCallback;Z)V"},
		cancellable = true)
	private void onLoadSkin(GameProfile profile,
		PlayerSkinProvider.SkinTextureAvailableCallback callback,
		boolean requireSecure, CallbackInfo ci)
	{
		// Can't @Inject nicely because everything is wrapped in a lambda.
		// Had to replace the whole method.
		
		EXECUTOR_SERVICE.submit(() -> {
			HashMap<MinecraftProfileTexture.Type, MinecraftProfileTexture> map =
				Maps.newHashMap();
			
			try
			{
				map.putAll(sessionService.getTextures(profile, requireSecure));
			}catch(InsecureTextureException var7)
			{
				
			}
			
			if(map.isEmpty())
			{
				profile.getProperties().clear();
				if(profile.getId().equals(MinecraftClient.getInstance()
					.getSession().getProfile().getId()))
				{
					profile.getProperties().putAll(
						MinecraftClient.getInstance().getSessionProperties());
					map.putAll(sessionService.getTextures(profile, false));
				}else
				{
					sessionService.fillProfileProperties(profile,
						requireSecure);
					
					try
					{
						map.putAll(
							sessionService.getTextures(profile, requireSecure));
					}catch(InsecureTextureException var6)
					{
						
					}
				}
			}
			
			addWurstCape(profile, map);
			
			MinecraftClient.getInstance().execute(() -> {
				if(map.containsKey(Type.SKIN))
					loadSkin(map.get(Type.SKIN), Type.SKIN, callback);
				
				if(map.containsKey(Type.CAPE))
					loadSkin(map.get(Type.CAPE), Type.CAPE, callback);
				
			});
		});
		
		ci.cancel();
	}
	
	private void addWurstCape(GameProfile profile,
		HashMap<MinecraftProfileTexture.Type, MinecraftProfileTexture> map)
	{
		try
		{
			if(capes == null)
				setupWurstCapes();
			
			System.out.println(profile.getName());
			if(capes.has(profile.getName()))
			{
				String capeURL = capes.get(profile.getName()).getAsString();
				map.put(Type.CAPE, new MinecraftProfileTexture(capeURL, null));
			}
			
		}catch(Exception e)
		{
			System.err.println(
				"[Wurst] Failed to load cape for '" + profile.getName() + "'");
			
			e.printStackTrace();
		}
	}
	
	private void setupWurstCapes()
	{
		try
		{
			// TODO: download capes to file
			URL url = new URL("https://www.wurstclient.net/api/v1/capes.json");
			
			capes = JsonUtils.JSON_PARSER
				.parse(new InputStreamReader(url.openStream()))
				.getAsJsonObject();
			
		}catch(Exception e)
		{
			System.err
				.println("[Wurst] Failed to load capes from wurstclient.net!");
			
			e.printStackTrace();
		}
	}
	
	@Shadow
	public Identifier loadSkin(MinecraftProfileTexture profileTexture,
		Type type,
		@Nullable PlayerSkinProvider.SkinTextureAvailableCallback callback)
	{
		return null;
	}
}