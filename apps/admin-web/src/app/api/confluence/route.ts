import { NextResponse } from "next/server";
import { confluenceCatalogJson } from "@/lib/confluence/catalog";

export async function GET() {
  return NextResponse.json(confluenceCatalogJson());
}
